package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateTrackerTest {

    private static final Duration TICK = Duration.ofMillis(50);

    private final FakeClock time = new FakeClock();
    private final RateTracker<String> tracker = new RateTracker<>();

    /** Ticks at 20 TPS for {@code duration}, recording one {@code item} every {@code every} ticks. */
    private void run(Duration duration, String item, int every) {
        long ticks = duration.dividedBy(TICK);
        for (long i = 1; i <= ticks; i++) {
            time.advance(TICK);
            tracker.tick(time.instant());
            if (every > 0 && i % every == 0) {
                tracker.record(item, 1);
            }
        }
    }

    @Test
    void measuresItemsPerHourOfRunningTime() {
        tracker.tick(time.instant());
        // One item every 72 ticks (3.6 s) is 1000 items per hour.
        run(Duration.ofMinutes(30), "iron", 72);

        assertThat(tracker.rates().get("iron").itemsPerHour()).isCloseTo(1000, within(1.0));
        assertThat(tracker.observed()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void pausesAreExcluded() {
        tracker.tick(time.instant());
        run(Duration.ofMinutes(18), "iron", 72);
        time.advance(Duration.ofHours(2));
        tracker.tick(time.instant());
        time.advance(Duration.ofSeconds(-30));
        tracker.tick(time.instant());
        run(Duration.ofMinutes(18), "iron", 72);

        assertThat(tracker.observed()).isEqualTo(Duration.ofMinutes(36));
        assertThat(tracker.rates().get("iron").itemsPerHour()).isCloseTo(1000, within(1.0));
    }

    @Test
    void gapOfExactlyOneSecondStillCounts() {
        tracker.tick(time.instant());
        time.advance(Duration.ofSeconds(1));
        tracker.tick(time.instant());
        time.advance(Duration.ofMillis(1001));
        tracker.tick(time.instant());

        assertThat(tracker.observed()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void warmUpGateOpensAfterFiveMinutesOfRunning() {
        tracker.tick(time.instant());
        run(Duration.ofMinutes(4), "iron", 72);
        assertThat(tracker.isWarmedUp(RateTracker.DEFAULT_WARM_UP)).isFalse();

        run(Duration.ofMinutes(1), "iron", 72);
        assertThat(tracker.isWarmedUp(RateTracker.DEFAULT_WARM_UP)).isTrue();
    }

    @Test
    void windowRollsOverTheLastHourOfRunningTime() {
        tracker.tick(time.instant());
        run(Duration.ofMinutes(60), "iron", 72);
        // A quiet minute, so no bucket holds both farms.
        run(Duration.ofMinutes(1), "iron", 0);
        run(Duration.ofMinutes(60), "gold", 36);

        Map<String, Rate> rates = tracker.rates();
        assertThat(rates).doesNotContainKey("iron");
        assertThat(rates.get("gold").itemsPerHour()).isCloseTo(2000, within(40.0));
        assertThat(tracker.observed()).isBetween(RateTracker.WINDOW, RateTracker.WINDOW.plusMinutes(1));
    }

    @Test
    void shortSessionBlendsIntoThePreviousMeasurement() {
        tracker.tick(time.instant());
        run(Duration.ofMinutes(60), "iron", 72);
        time.advance(Duration.ofHours(8));
        tracker.tick(time.instant());
        run(Duration.ofMinutes(3), "iron", 72);

        assertThat(tracker.isWarmedUp(RateTracker.DEFAULT_WARM_UP)).isTrue();
        assertThat(tracker.rates().get("iron").itemsPerHour()).isCloseTo(1000, within(20.0));
    }

    @Test
    void clockGoingBackwardsAddsNoTimeAndKeepsCounting() {
        time.advance(Duration.ofMinutes(5));
        tracker.tick(time.instant());
        time.advance(Duration.ofMinutes(-2));
        tracker.tick(time.instant());
        tracker.record("iron", 3);

        assertThat(tracker.observed()).isZero();
        assertThat(tracker.buckets())
                .singleElement()
                .satisfies(bucket -> assertThat(bucket.counts()).containsEntry("iron", 3L));
    }

    @Test
    void ratesAreEmptyUntilTimeIsObserved() {
        tracker.tick(time.instant());
        tracker.record("iron", 5);

        assertThat(tracker.rates()).isEmpty();
    }

    @Test
    void recordNeedsATickAndANonNegativeCount() {
        assertThatThrownBy(() -> tracker.record("iron", 1)).isInstanceOf(IllegalStateException.class);

        tracker.tick(time.instant());
        assertThatThrownBy(() -> tracker.record("iron", -1)).isInstanceOf(IllegalArgumentException.class);

        tracker.record("iron", 0);
        assertThat(tracker.buckets())
                .singleElement()
                .satisfies(bucket -> assertThat(bucket.counts()).isEmpty());
    }

    @Test
    void restoresFromSavedBuckets() {
        tracker.tick(time.instant());
        run(Duration.ofMinutes(20), "iron", 72);
        List<RateTracker.Bucket<String>> saved = tracker.buckets();

        RateTracker<String> restored = new RateTracker<>(saved.reversed());

        assertThat(restored.buckets()).isEqualTo(saved);
        assertThat(restored.rates()).isEqualTo(tracker.rates());
        assertThat(restored.observed()).isEqualTo(tracker.observed());
    }

    @Test
    void bucketCountIsBoundedForBurstyTicking() {
        for (int minute = 0; minute < 300; minute++) {
            tracker.tick(time.instant());
            time.advance(Duration.ofMillis(500));
            tracker.tick(time.instant());
            tracker.record("iron", 1);
            time.advance(Duration.ofMillis(59_500));
        }

        assertThat(tracker.buckets()).hasSize(RateTracker.MAX_BUCKETS);
        assertThat(tracker.observed()).isEqualTo(Duration.ofMillis(500L * RateTracker.MAX_BUCKETS));
    }

    @Test
    void bucketRejectsNegativeValues() {
        assertThatThrownBy(() -> new RateTracker.Bucket<>(0, -1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateTracker.Bucket<>(0, 0, Map.of("iron", -1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
