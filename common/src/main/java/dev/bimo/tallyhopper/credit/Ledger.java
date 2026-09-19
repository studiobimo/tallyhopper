package dev.bimo.tallyhopper.credit;

import com.mojang.serialization.Codec;
import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.offline.Backlog;
import dev.bimo.tallyhopper.offline.OfflineCredit;
import dev.bimo.tallyhopper.offline.Rate;
import dev.bimo.tallyhopper.offline.SessionClock;
import dev.bimo.tallyhopper.session.OfflineSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One Tally Hopper's account of offline credit: its backlog, the fractions of items carried between
 * sessions, when it last ticked, and which session it was last credited for.
 */
public final class Ledger {

    /** Codec for {@code item → count} maps. Items removed from the game fail to decode and are dropped. */
    public static final Codec<Map<Item, Long>> COUNTS_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), ExtraCodecs.NON_NEGATIVE_LONG);

    private static final String BACKLOG_KEY = TallyHopper.MOD_ID + "_backlog";
    private static final String CARRY_KEY = TallyHopper.MOD_ID + "_carry";
    private static final String LAST_TICK_KEY = TallyHopper.MOD_ID + "_last_tick";
    private static final String CREDITED_SESSION_KEY = TallyHopper.MOD_ID + "_credited_session";

    /** How often the last tick is written to the chunk; well inside the eligibility grace. */
    private static final Duration LAST_TICK_SAVE_INTERVAL = SessionClock.HEARTBEAT_INTERVAL;

    private Backlog<Item> backlog = new Backlog<>(Backlog.DEFAULT_CAP);
    private OfflineCredit<Item> carry = new OfflineCredit<>();
    private @Nullable Instant lastTick;
    private @Nullable Instant lastTickSaved;
    private long creditedSession;

    public Backlog<Item> backlog() {
        return backlog;
    }

    /** Whether this hopper still has to be credited for {@code session}. */
    public boolean needsCredit(OfflineSession.Current session) {
        return creditedSession != session.id();
    }

    /**
     * Marks {@code session} credited and returns the whole items this hopper earned in its window at
     * {@code rates}. A hopper that never ticked, or wasn't running when the world closed, earns nothing.
     */
    public Map<Item, Long> earn(OfflineSession.Current session, Map<Item, Rate> rates) {
        creditedSession = session.id();
        Instant last = lastTick;
        if (last == null) {
            return Map.of();
        }
        return carry.accrue(rates, session.window().creditFor(last));
    }

    /**
     * Records that the hopper ticked at {@code now}. Returns {@code true} when the block entity should be
     * marked changed, so the chunk saves a recent enough last tick.
     */
    public boolean ticked(Instant now) {
        lastTick = now;
        Instant saved = lastTickSaved;
        if (saved == null || Duration.between(saved, now).compareTo(LAST_TICK_SAVE_INTERVAL) >= 0) {
            lastTickSaved = now;
            return true;
        }
        return false;
    }

    /** Replaces the backlog, for a hopper placed from an item that carried one. */
    public void restoreBacklog(StoredBacklog stored) {
        backlog = new Backlog<>(stored.counts(), Backlog.DEFAULT_CAP);
    }

    /** Leaves the backlog out of block entity data that is saved alongside the item's own component. */
    public static void discardBacklog(ValueOutput output) {
        output.discard(BACKLOG_KEY);
    }

    public void save(ValueOutput output) {
        if (!backlog.isEmpty()) {
            output.store(BACKLOG_KEY, COUNTS_CODEC, backlog.contents());
        }
        Map<Item, Long> carried = carry.carry();
        if (!carried.isEmpty()) {
            output.store(CARRY_KEY, COUNTS_CODEC, carried);
        }
        Instant last = lastTick;
        if (last != null) {
            output.putLong(LAST_TICK_KEY, last.toEpochMilli());
        }
        output.putLong(CREDITED_SESSION_KEY, creditedSession);
    }

    public void load(ValueInput input) {
        backlog = new Backlog<>(input.read(BACKLOG_KEY, COUNTS_CODEC).orElse(Map.of()), Backlog.DEFAULT_CAP);
        try {
            carry = new OfflineCredit<>(input.read(CARRY_KEY, COUNTS_CODEC).orElse(Map.of()));
        } catch (IllegalArgumentException e) {
            // A carry is under one item per type; losing it costs less than an item.
            TallyHopper.LOG.warn("Discarding an unreadable Tally Hopper carry", e);
            carry = new OfflineCredit<>();
        }
        lastTick = input.getLong(LAST_TICK_KEY).map(Instant::ofEpochMilli).orElse(null);
        lastTickSaved = lastTick;
        creditedSession = input.getLongOr(CREDITED_SESSION_KEY, 0);
    }
}
