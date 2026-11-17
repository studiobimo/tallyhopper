package dev.bimo.tallyhopper.session;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.offline.OfflineWindow;
import dev.bimo.tallyhopper.offline.SessionClock;
import dev.bimo.tallyhopper.registry.TallyHopperGameRules;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

public final class OfflineSession {

    public record Current(long id, OfflineWindow window) {}

    private record Running(
            SessionClock clock, SessionData data, TickTime tickTime, Current current, RejoinSummary summary) {}

    private static @Nullable Running running;

    private OfflineSession() {}

    public static @Nullable Current current() {
        Running now = running;
        return now == null ? null : now.current();
    }

    public static void onServerStarted(MinecraftServer server) {
        SessionData data = server.getDataStorage().computeIfAbsent(SessionData.TYPE);
        TickTime tickTime = new TickTime(Instant.now());
        SessionClock clock = new SessionClock(tickTime, data.lastHeartbeat(), data.sessionId());
        OfflineWindow window =
                clock.startSession(Duration.ofHours(server.getGameRules().get(TallyHopperGameRules.MAX_OFFLINE_HOURS)));
        data.update(clock);
        running = new Running(clock, data, tickTime, new Current(clock.sessionId(), window), new RejoinSummary());
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
        if (now.summary().isSettled()) {
            now.summary().send(server, now.current().window());
        }
        if (now.clock().isHeartbeatDue()) {
            now.clock().heartbeat();
            now.data().update(now.clock());
        }
    }

    public static void reportCredit(BlockPos pos, CreditReport report) {
        Running now = running;
        if (now != null && !report.isEmpty()) {
            now.summary().add(pos, report);
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        Running now = running;
        if (now == null) {
            return;
        }
        now.clock().heartbeat();
        now.data().update(now.clock());
        running = null;
    }

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
