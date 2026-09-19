package dev.bimo.tallyhopper.offline;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import org.jspecify.annotations.Nullable;

/**
 * Tracks when a world was last seen running, so the next session knows how long it was closed.
 *
 * <p>The loader layer persists {@link #lastHeartbeat()} and {@link #sessionId()}, calls {@link
 * #heartbeat()} whenever {@link #isHeartbeatDue()} (and on server stop), and skips heartbeats while
 * an integrated server is paused. A crash therefore loses at most one {@link #HEARTBEAT_INTERVAL}.
 * Time is UTC epoch-based, so daylight saving changes don't matter.
 */
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

    /**
     * Starts a new session and returns the window the world spent closed.
     *
     * <p>A new heartbeat is written immediately, so the same window can never be claimed twice.
     */
    public OfflineWindow startSession(Duration maxOffline) {
        Instant now = time.instant();
        OfflineWindow window =
                lastHeartbeat == null ? OfflineWindow.none(now) : OfflineWindow.between(lastHeartbeat, now, maxOffline);
        lastHeartbeat = now;
        sessionId++;
        return window;
    }

    /** Records that the world is running right now. */
    public void heartbeat() {
        lastHeartbeat = time.instant();
    }

    /** Whether a heartbeat is due. A clock that jumped backwards also triggers one, to re-anchor. */
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

    /** Increments once per session. Hoppers store the last id they were credited for. */
    public long sessionId() {
        return sessionId;
    }
}
