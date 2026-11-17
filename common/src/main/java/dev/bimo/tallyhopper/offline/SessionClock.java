package dev.bimo.tallyhopper.offline;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import org.jspecify.annotations.Nullable;

public final class SessionClock {

    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    public static final Duration DEFAULT_MAX_OFFLINE = Duration.ofHours(24);

    private final InstantSource time;
    private @Nullable Instant lastHeartbeat;
    private long sessionId;

    /**
     * @param lastHeartbeat the persisted heartbeat, or {@code null} for a world that never had one
     * @param sessionId the persisted session id, or {@code 0} for a new world
     */
    public SessionClock(InstantSource time, @Nullable Instant lastHeartbeat, long sessionId) {
        this.time = time;
        this.lastHeartbeat = lastHeartbeat;
        this.sessionId = sessionId;
    }

    public OfflineWindow startSession(Duration maxOffline) {
        Instant now = time.instant();
        OfflineWindow window =
                lastHeartbeat == null ? OfflineWindow.none(now) : OfflineWindow.between(lastHeartbeat, now, maxOffline);
        lastHeartbeat = now;
        sessionId++;
        return window;
    }

    public void heartbeat() {
        lastHeartbeat = time.instant();
    }

    public boolean isHeartbeatDue() {
        Instant last = lastHeartbeat;
        if (last == null) {
            return true;
        }
        Duration since = Duration.between(last, time.instant());
        return since.isNegative() || since.compareTo(HEARTBEAT_INTERVAL) >= 0;
    }

    public @Nullable Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    public long sessionId() {
        return sessionId;
    }
}
