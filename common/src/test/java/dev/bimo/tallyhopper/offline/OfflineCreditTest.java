package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfflineCreditTest {

    private final OfflineCredit<String> credit = new OfflineCredit<>();

    @Test
    void thousandPerHourForEightHoursIsEightThousand() {
        Map<String, Long> earned = credit.accrue(Map.of("iron", Rate.perHour(1000)), Duration.ofHours(8));

        assertThat(earned).containsExactly(Map.entry("iron", 8000L));
        assertThat(credit.carry()).isEmpty();
    }

    @Test
    void fractionsCarryIntoTheNextSession() {
        Map<String, Rate> onePerHour = Map.of("sponge", Rate.perHour(1));

        assertThat(credit.accrue(onePerHour, Duration.ofMinutes(30))).isEmpty();
        assertThat(credit.carry()).containsEntry("sponge", OfflineCredit.MICROS_PER_ITEM / 2);

        assertThat(credit.accrue(onePerHour, Duration.ofMinutes(30))).containsExactly(Map.entry("sponge", 1L));
        assertThat(credit.carry()).isEmpty();
    }

    @Test
    void carrySurvivesASaveAndRestore() {
        credit.accrue(Map.of("sponge", Rate.perHour(1)), Duration.ofMinutes(45));

        OfflineCredit<String> restored = new OfflineCredit<>(credit.carry());

        assertThat(restored.accrue(Map.of("sponge", Rate.perHour(1)), Duration.ofMinutes(15)))
                .containsExactly(Map.entry("sponge", 1L));
    }

    @Test
    void carryIsKeptForItemsMissingThisSession() {
        credit.accrue(Map.of("sponge", Rate.perHour(1)), Duration.ofMinutes(30));
        credit.accrue(Map.of("iron", Rate.perHour(1000)), Duration.ofHours(1));

        assertThat(credit.carry()).containsOnlyKeys("sponge");
    }

    @Test
    void roundingNeverCreatesItems() {
        Map<String, Rate> oneEveryThreeHours =
                Map.of("sponge", new Rate(1, Duration.ofHours(3).toMillis()));

        for (int session = 0; session < 3; session++) {
            assertThat(credit.accrue(oneEveryThreeHours, Duration.ofHours(1))).isEmpty();
        }

        assertThat(credit.carry()).containsEntry("sponge", OfflineCredit.MICROS_PER_ITEM - 1);
    }

    @Test
    void negativeClockJumpEarnsNothing() {
        Instant closed = FakeClock.EPOCH;
        OfflineWindow window =
                OfflineWindow.between(closed, closed.minus(Duration.ofHours(6)), SessionClock.DEFAULT_MAX_OFFLINE);

        assertThat(credit.accrue(Map.of("iron", Rate.perHour(1000)), window.credited()))
                .isEmpty();
        assertThatThrownBy(() -> credit.accrue(Map.of(), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hundredDayGapWithARaisedCap() {
        Instant closed = FakeClock.EPOCH;
        OfflineWindow window = OfflineWindow.between(closed, closed.plus(Duration.ofDays(100)), Duration.ofHours(2400));

        Map<String, Long> earned = credit.accrue(Map.of("iron", Rate.perHour(1_000_000)), window.credited());

        assertThat(earned).containsExactly(Map.entry("iron", 2_400_000_000L));
    }

    @Test
    void hundredDayGapWithTheDefaultCapIsCutToADay() {
        Instant closed = FakeClock.EPOCH;
        OfflineWindow window =
                OfflineWindow.between(closed, closed.plus(Duration.ofDays(100)), SessionClock.DEFAULT_MAX_OFFLINE);

        Map<String, Long> earned = credit.accrue(Map.of("iron", Rate.perHour(1000)), window.credited());

        assertThat(window.capped()).isTrue();
        assertThat(earned).containsExactly(Map.entry("iron", 24_000L));
    }

    @Test
    void absurdRatesSaturateInsteadOfOverflowing() {
        Map<String, Long> earned = credit.accrue(Map.of("iron", new Rate(Long.MAX_VALUE, 1)), Duration.ofDays(100_000));

        assertThat(earned).containsExactly(Map.entry("iron", Long.MAX_VALUE));
        assertThat(credit.carry()).isEmpty();
    }

    @Test
    void overrideReplacesTheMeasuredRate() {
        Map<String, Rate> rates = OfflineCredit.effectiveRates(
                Map.of("iron", Rate.perHour(1000), "poppy", Rate.perHour(40)),
                true,
                Map.of("iron", Rate.perHour(50), "gold", Rate.perHour(10)));

        assertThat(rates)
                .containsOnly(
                        Map.entry("iron", Rate.perHour(50)),
                        Map.entry("poppy", Rate.perHour(40)),
                        Map.entry("gold", Rate.perHour(10)));
    }

    @Test
    void zeroOverrideSwitchesAnItemOff() {
        Map<String, Rate> rates =
                OfflineCredit.effectiveRates(Map.of("iron", Rate.perHour(1000)), true, Map.of("iron", Rate.perHour(0)));

        assertThat(credit.accrue(rates, Duration.ofHours(8))).isEmpty();
    }

    @Test
    void warmUpGateHoldsBackMeasuredRatesButNotOverrides() {
        Map<String, Rate> rates = OfflineCredit.effectiveRates(
                Map.of("iron", Rate.perHour(1000)), false, Map.of("gold", Rate.perHour(10)));

        assertThat(rates).containsOnly(Map.entry("gold", Rate.perHour(10)));
    }

    @Test
    void rejectsCarryOfAWholeItemOrMore() {
        assertThatThrownBy(() -> new OfflineCredit<>(Map.of("iron", OfflineCredit.MICROS_PER_ITEM)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfflineCredit<>(Map.of("iron", -1L))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new OfflineCredit<>(Map.of("iron", 0L)).carry()).isEmpty();
    }

    @Test
    void measuredFarmCreditsItsBacklogEndToEnd() {
        FakeClock time = new FakeClock();
        RateTracker<String> tracker = new RateTracker<>();
        tracker.tick(time.instant());
        for (int tick = 1; tick <= 20 * 60 * 30; tick++) {
            time.advance(Duration.ofMillis(50));
            tracker.tick(time.instant());
            if (tick % 72 == 0) {
                tracker.record("iron", 1);
            }
        }
        SessionClock clock = new SessionClock(time, time.instant(), 1);
        time.advance(Duration.ofHours(8));
        OfflineWindow window = clock.startSession(SessionClock.DEFAULT_MAX_OFFLINE);
        Backlog<String> backlog = new Backlog<>(5000);

        Map<String, Long> earned = credit.accrue(
                OfflineCredit.effectiveRates(
                        tracker.rates(), tracker.isWarmedUp(RateTracker.DEFAULT_WARM_UP), Map.of()),
                window.credited());
        long refused = backlog.addAll(earned);

        assertThat(earned).containsExactly(Map.entry("iron", 8000L));
        assertThat(backlog.get("iron")).isEqualTo(5000);
        assertThat(refused).isEqualTo(3000);
    }
}
