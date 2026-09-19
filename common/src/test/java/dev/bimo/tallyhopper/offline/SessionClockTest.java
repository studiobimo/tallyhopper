package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SessionClockTest {

    private final FakeClock time = new FakeClock();

    @Test
    void firstSessionCreditsNothing() {
        SessionClock clock = new SessionClock(time, null, 0);

        OfflineWindow window = clock.startSession(SessionClock.DEFAULT_MAX_OFFLINE);

        assertThat(window.credited()).isZero();
        assertThat(clock.sessionId()).isEqualTo(1);
        assertThat(clock.lastHeartbeat()).isEqualTo(FakeClock.EPOCH);
    }

    @Test
    void creditsTimeSinceTheLastHeartbeat() {
        SessionClock clock = new SessionClock(time, FakeClock.EPOCH, 7);
        time.advance(Duration.ofHours(8));

        OfflineWindow window = clock.startSession(SessionClock.DEFAULT_MAX_OFFLINE);

        assertThat(window.from()).isEqualTo(FakeClock.EPOCH);
        assertThat(window.credited()).isEqualTo(Duration.ofHours(8));
        assertThat(clock.sessionId()).isEqualTo(8);
    }

    @Test
    void startingWritesAHeartbeatSoTheWindowCannotBeClaimedTwice() {
        SessionClock clock = new SessionClock(time, FakeClock.EPOCH, 0);
        time.advance(Duration.ofHours(8));
        clock.startSession(SessionClock.DEFAULT_MAX_OFFLINE);

        OfflineWindow again = clock.startSession(SessionClock.DEFAULT_MAX_OFFLINE);

        assertThat(again.credited()).isZero();
    }

    @Test
    void appliesTheCap() {
        SessionClock clock = new SessionClock(time, FakeClock.EPOCH, 0);
        time.advance(Duration.ofDays(100));

        OfflineWindow window = clock.startSession(SessionClock.DEFAULT_MAX_OFFLINE);

        assertThat(window.credited()).isEqualTo(Duration.ofHours(24));
        assertThat(window.capped()).isTrue();
    }

    @Test
    void clockGoingBackwardsCreditsNothing() {
        SessionClock clock = new SessionClock(time, FakeClock.EPOCH, 0);
        time.advance(Duration.ofHours(-5));

        assertThat(clock.startSession(SessionClock.DEFAULT_MAX_OFFLINE).credited())
                .isZero();
    }

    @Test
    void heartbeatIsDueEveryThirtySeconds() {
        SessionClock clock = new SessionClock(time, null, 0);
        assertThat(clock.isHeartbeatDue()).isTrue();

        clock.heartbeat();
        assertThat(clock.isHeartbeatDue()).isFalse();

        time.advance(Duration.ofSeconds(29));
        assertThat(clock.isHeartbeatDue()).isFalse();

        time.advance(Duration.ofSeconds(1));
        assertThat(clock.isHeartbeatDue()).isTrue();

        clock.heartbeat();
        assertThat(clock.lastHeartbeat()).isEqualTo(time.instant());
    }

    @Test
    void heartbeatIsDueAfterTheClockJumpsBackwards() {
        SessionClock clock = new SessionClock(time, FakeClock.EPOCH, 0);
        time.advance(Duration.ofSeconds(-1));

        assertThat(clock.isHeartbeatDue()).isTrue();
    }
}
