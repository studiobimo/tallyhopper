package dev.bimo.tallyhopper.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.offline.SessionClock;
import java.time.Instant;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** The {@link SessionClock} state, saved with the world in {@code data/tallyhopper/session.dat}. */
final class SessionData extends SavedData {

    private static final Codec<SessionData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.optionalFieldOf("last_heartbeat").forGetter(SessionData::lastHeartbeatMillis),
                    Codec.LONG.fieldOf("session_id").forGetter(SessionData::sessionId))
            .apply(i, SessionData::new));

    // No data fixer: this is our own format. Both loaders accept a null fixer type.
    @SuppressWarnings("NullAway")
    static final SavedDataType<SessionData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, "session"), SessionData::new, CODEC, null);

    private @Nullable Instant lastHeartbeat;
    private long sessionId;

    private SessionData() {}

    private SessionData(Optional<Long> lastHeartbeatMillis, long sessionId) {
        this.lastHeartbeat = lastHeartbeatMillis.map(Instant::ofEpochMilli).orElse(null);
        this.sessionId = sessionId;
    }

    @Nullable Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    long sessionId() {
        return sessionId;
    }

    /** Copies the clock's state in, to be written with the next save. */
    void update(SessionClock clock) {
        lastHeartbeat = clock.lastHeartbeat();
        sessionId = clock.sessionId();
        setDirty();
    }

    private Optional<Long> lastHeartbeatMillis() {
        return Optional.ofNullable(lastHeartbeat).map(Instant::toEpochMilli);
    }
}
