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

public final class Ledger {

    public static final Codec<Map<Item, Long>> COUNTS_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), ExtraCodecs.NON_NEGATIVE_LONG);

    private static final String BACKLOG_KEY = TallyHopper.MOD_ID + "_backlog";
    private static final String CARRY_KEY = TallyHopper.MOD_ID + "_carry";
    private static final String LAST_TICK_KEY = TallyHopper.MOD_ID + "_last_tick";
    private static final String CREDITED_SESSION_KEY = TallyHopper.MOD_ID + "_credited_session";
    private static final String LAST_CREDIT_KEY = TallyHopper.MOD_ID + "_last_credit";

    private static final Duration LAST_TICK_SAVE_INTERVAL = SessionClock.HEARTBEAT_INTERVAL;

    private Backlog<Item> backlog = new Backlog<>(Backlog.DEFAULT_CAP);
    private OfflineCredit<Item> carry = new OfflineCredit<>();
    private @Nullable Instant lastTick;
    private @Nullable Instant lastTickSaved;
    private long creditedSession;
    private long lastCredit;

    public Backlog<Item> backlog() {
        return backlog;
    }

    public boolean needsCredit(OfflineSession.Current session) {
        return creditedSession != session.id();
    }

    public Map<Item, Long> earn(OfflineSession.Current session, Map<Item, Rate> rates) {
        creditedSession = session.id();
        Instant last = lastTick;
        if (last == null) {
            return Map.of();
        }
        return carry.accrue(rates, session.window().creditFor(last));
    }

    public long lastCredit() {
        return lastCredit;
    }

    public void setLastCredit(long items) {
        lastCredit = items;
    }

    public boolean ticked(Instant now) {
        lastTick = now;
        Instant saved = lastTickSaved;
        if (saved == null || Duration.between(saved, now).compareTo(LAST_TICK_SAVE_INTERVAL) >= 0) {
            lastTickSaved = now;
            return true;
        }
        return false;
    }

    public void restoreBacklog(StoredBacklog stored) {
        backlog = new Backlog<>(stored.counts(), Backlog.DEFAULT_CAP);
    }

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
        output.putLong(LAST_CREDIT_KEY, lastCredit);
    }

    public void load(ValueInput input) {
        backlog = new Backlog<>(input.read(BACKLOG_KEY, COUNTS_CODEC).orElse(Map.of()), Backlog.DEFAULT_CAP);
        try {
            carry = new OfflineCredit<>(input.read(CARRY_KEY, COUNTS_CODEC).orElse(Map.of()));
        } catch (IllegalArgumentException e) {
            TallyHopper.LOG.warn("Discarding an unreadable Tally Hopper carry", e);
            carry = new OfflineCredit<>();
        }
        lastTick = input.getLong(LAST_TICK_KEY).map(Instant::ofEpochMilli).orElse(null);
        lastTickSaved = lastTick;
        creditedSession = input.getLongOr(CREDITED_SESSION_KEY, 0);
        lastCredit = input.getLongOr(LAST_CREDIT_KEY, 0);
    }
}
