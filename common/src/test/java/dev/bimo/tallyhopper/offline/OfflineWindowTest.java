package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OfflineWindowTest {

    private static final Instant T0 = FakeClock.EPOCH;
    private static final Duration DAY = Duration.ofHours(24);

    @Test
    void creditsTheWholeGapWithinTheCap() {
        OfflineWindow window = OfflineWindow.between(T0, T0.plus(Duration.ofHours(8)), DAY);

        assertThat(window.credited()).isEqualTo(Duration.ofHours(8));
        assertThat(window.capped()).isFalse();
        assertThat(window.raw()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void negativeGapEarnsNothing() {
        OfflineWindow window = OfflineWindow.between(T0, T0.minus(Duration.ofHours(3)), DAY);

        assertThat(window.credited()).isZero();
        assertThat(window.capped()).isFalse();
        assertThat(window.raw()).isNegative();
    }

    @Test
    void gapUnderAMinuteEarnsNothing() {
        assertThat(OfflineWindow.between(T0, T0.plusSeconds(59), DAY).credited())
                .isZero();
        assertThat(OfflineWindow.between(T0, T0.plusSeconds(60), DAY).credited())
                .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void longGapIsCappedAndFlagged() {
        OfflineWindow window = OfflineWindow.between(T0, T0.plus(Duration.ofDays(3)), DAY);

        assertThat(window.credited()).isEqualTo(DAY);
        assertThat(window.capped()).isTrue();
    }

    @Test
    void gapExactlyAtTheCapIsNotFlagged() {
        OfflineWindow window = OfflineWindow.between(T0, T0.plus(DAY), DAY);

        assertThat(window.credited()).isEqualTo(DAY);
        assertThat(window.capped()).isFalse();
    }

    @Test
    void hundredDayGapWithARaisedCapIsCreditedInFull() {
        Duration hundredDays = Duration.ofDays(100);

        OfflineWindow window = OfflineWindow.between(T0, T0.plus(hundredDays), Duration.ofHours(2400));

        assertThat(window.credited()).isEqualTo(hundredDays);
        assertThat(window.capped()).isFalse();
    }

    @Test
    void zeroCapCreditsNothingButReportsTheCap() {
        OfflineWindow window = OfflineWindow.between(T0, T0.plus(Duration.ofHours(1)), Duration.ZERO);

        assertThat(window.credited()).isZero();
        assertThat(window.capped()).isTrue();
    }

    @Test
    void rejectsNegativeCapAndNegativeCredit() {
        assertThatThrownBy(() -> OfflineWindow.between(T0, T0, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfflineWindow(T0, T0, Duration.ofSeconds(-1), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noneCreditsNothing() {
        OfflineWindow window = OfflineWindow.none(T0);

        assertThat(window.credited()).isZero();
        assertThat(window.capped()).isFalse();
    }

    @Test
    void hopperTickingNearTheFinalHeartbeatIsEligible() {
        OfflineWindow window = OfflineWindow.between(T0, T0.plus(Duration.ofHours(8)), DAY);

        assertThat(window.isEligible(T0.plusSeconds(20))).isTrue();
        assertThat(window.isEligible(T0.minus(Duration.ofMinutes(2)))).isTrue();
        assertThat(window.isEligible(T0.minus(Duration.ofMinutes(2)).minusMillis(1)))
                .isFalse();
    }
}
