package dev.bimo.tallyhopper.session;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.offline.OfflineWindow;
import dev.bimo.tallyhopper.offline.SessionClock;
import java.time.Instant;
import java.time.InstantSource;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The running server's session: how long the world was closed, and the heartbeat that lets the next
 * session work that out.
 *
 * <p>Each loader calls {@link #onServerStarted}, {@link #onServerTick} and {@link #onServerStopping}.
 * Heartbeats use the time of the last server tick, not the time they are written. A paused
 * singleplayer world doesn't tick, so time spent paused counts as time away, exactly as if the player
 * had quit when they paused, and hoppers that were running when the game was paused stay eligible.
 */
public final class OfflineSession {

    /** A started session. Hoppers credit {@code window} once, and remember {@code id} to not do it again. */
    public record Current(long id, OfflineWindow window) {}

    private record Running(SessionClock clock, SessionData data, TickTime tickTime, Current current) {}

    private static @Nullable Running running;

    private OfflineSession() {}

    /** The session hoppers credit against, or {@code null} when no server has started yet. */
    public static @Nullable Current current() {
        Running now = running;
        return now == null ? null : now.current();
    }

    public static void onServerStarted(MinecraftServer server) {
        SessionData data = server.getDataStorage().computeIfAbsent(SessionData.TYPE);
        TickTime tickTime = new TickTime(Instant.now());
        SessionClock clock = new SessionClock(tickTime, data.lastHeartbeat(), data.sessionId());
        OfflineWindow window = clock.startSession(SessionClock.DEFAULT_MAX_OFFLINE);
        data.update(clock);
        running = new Running(clock, data, tickTime, new Current(clock.sessionId(), window));
        TallyHopper.LOG.info(
                "Session {} started; the world was closed for {} ({} credited{})",
                clock.sessionId(),
                window.raw(),
                window.credited(),
                window.capped() ? ", capped" : "");
    }

    public static void onServerTick(MinecraftServer server) {
        Running now = running;
        if (now == null) {
            return;
        }
        now.tickTime().set(Instant.now());
        if (now.clock().isHeartbeatDue()) {
            now.clock().heartbeat();
            now.data().update(now.clock());
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        Running now = running;
        if (now == null) {
            return;
        }
        // The world is saved right after this, so the final heartbeat is the last tick.
        now.clock().heartbeat();
        now.data().update(now.clock());
        running = null;
    }

    /** The time of the most recent server tick. */
    private static final class TickTime implements InstantSource {

        private Instant time;

        TickTime(Instant time) {
            this.time = time;
        }

        void set(Instant time) {
            this.time = time;
        }

        @Override
        public Instant instant() {
            return time;
        }
    }
}
