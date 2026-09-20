package dev.bimo.tallyhopper.measure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.offline.OfflineCredit;
import dev.bimo.tallyhopper.offline.Rate;
import dev.bimo.tallyhopper.offline.RateBounds;
import dev.bimo.tallyhopper.offline.RateTracker;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What one Tally Hopper has measured: a {@link RateTracker} keyed by item, the player's per-item
 * overrides, and saving and loading for both.
 *
 * <p>Only stacks with default components are counted. A randomly enchanted bow or a named item is
 * unique, so extrapolating it would fabricate items that never existed.
 */
public final class Measurement {

    private static final String BUCKETS_KEY = TallyHopper.MOD_ID + "_rate";
    private static final String OVERRIDES_KEY = TallyHopper.MOD_ID + "_overrides";
    private static final Codec<Map<Item, Long>> OVERRIDES_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), ExtraCodecs.NON_NEGATIVE_LONG);
    private static final Codec<RateTracker.Bucket<Item>> BUCKET_CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.fieldOf("minute").forGetter(RateTracker.Bucket::minute),
                    Codec.LONG.fieldOf("active_ms").forGetter(RateTracker.Bucket::activeMillis),
                    Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.LONG)
                            .fieldOf("counts")
                            .forGetter(RateTracker.Bucket::counts))
            .apply(i, RateTracker.Bucket::new));

    private RateTracker<Item> tracker = new RateTracker<>();
    private Duration warmUp = RateTracker.DEFAULT_WARM_UP;
    private final Map<Item, Long> overridesPerHour = new LinkedHashMap<>();

    /** Whether a stack may be counted and later extrapolated. */
    public static boolean isCountable(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
    }

    /** The hopper ticked at {@code now}. */
    public void tick(Instant now) {
        tracker.tick(now);
    }

    /** Items entered through an intake path at {@code now}. */
    public void recordIntake(Item item, long count, Instant now) {
        // Intake can arrive before this hopper's own tick, from a neighbor that ticked first.
        tracker.tick(now);
        tracker.record(item, count);
    }

    public Map<Item, Rate> measuredRates() {
        return tracker.rates();
    }

    /**
     * Forgets what was measured and watches the farm again from nothing, for a player who changed
     * their farm and doesn't want to wait for the window to roll over. Overrides are left alone.
     */
    public void restart() {
        tracker = new RateTracker<>();
    }

    public Duration observed() {
        return tracker.observed();
    }

    /** How long the hopper must watch before its measured rate counts; set from a gamerule. */
    public Duration warmUp() {
        return warmUp;
    }

    public void setWarmUp(Duration warmUp) {
        this.warmUp = warmUp;
    }

    public boolean isWarmedUp() {
        return tracker.isWarmedUp(warmUp);
    }

    /**
     * Whether this hopper would earn offline credit: it has watched long enough, or the player set a
     * non-zero override.
     */
    public boolean isReady() {
        return isWarmedUp() || overridesPerHour.values().stream().anyMatch(perHour -> perHour > 0);
    }

    /**
     * The rates that earn credit, before the per-hopper ceiling: what was measured, weighed by how much
     * of the window this hopper has actually watched, with each override in place of its item's rate.
     *
     * @see OfflineCredit#effectiveRates
     * @see RateBounds#weigh
     */
    public Map<Item, Rate> effectiveRates() {
        Map<Item, Rate> overrides = new LinkedHashMap<>();
        overridesPerHour.forEach((item, perHour) -> overrides.put(item, Rate.perHour(perHour)));
        return OfflineCredit.effectiveRates(provenRates(), isWarmedUp(), overrides);
    }

    /**
     * What this hopper can prove on its own: its measured rates, weighed down while it has watched for
     * less than the window. This is the ceiling a player who is not an operator may override up to.
     */
    public Map<Item, Rate> provenRates() {
        return RateBounds.weigh(measuredRates(), observed(), RateTracker.WINDOW);
    }

    /** The overrides in items per hour. */
    public Map<Item, Long> overrides() {
        return Collections.unmodifiableMap(overridesPerHour);
    }

    /** Replaces the measured rate of {@code item}. Zero switches the item off. */
    public void setOverride(Item item, long perHour) {
        if (perHour < 0) {
            throw new IllegalArgumentException("perHour must not be negative: " + perHour);
        }
        overridesPerHour.put(item, perHour);
    }

    /** Returns whether {@code item} had an override. */
    public boolean clearOverride(Item item) {
        return overridesPerHour.remove(item) != null;
    }

    /** Returns how many overrides were removed. */
    public int clearOverrides() {
        int removed = overridesPerHour.size();
        overridesPerHour.clear();
        return removed;
    }

    /** How many of an item the current window counted. */
    public long counted(Item item) {
        long total = 0;
        for (RateTracker.Bucket<Item> bucket : tracker.buckets()) {
            total += bucket.counts().getOrDefault(item, 0L);
        }
        return total;
    }

    public void save(ValueOutput output) {
        output.store(BUCKETS_KEY, BUCKET_CODEC.listOf(), tracker.buckets());
        if (!overridesPerHour.isEmpty()) {
            output.store(OVERRIDES_KEY, OVERRIDES_CODEC, overridesPerHour);
        }
    }

    public void load(ValueInput input) {
        try {
            List<RateTracker.Bucket<Item>> buckets =
                    input.read(BUCKETS_KEY, BUCKET_CODEC.listOf()).orElse(List.of());
            tracker = new RateTracker<>(buckets);
        } catch (IllegalArgumentException e) {
            // A corrupt measurement is not worth losing the hopper's contents over; measure again.
            TallyHopper.LOG.warn("Discarding an unreadable Tally Hopper measurement", e);
            tracker = new RateTracker<>();
        }
        overridesPerHour.clear();
        // Vanilla reports entries that fail to decode (a removed item, say) and keeps the rest.
        input.read(OVERRIDES_KEY, OVERRIDES_CODEC).ifPresent(overridesPerHour::putAll);
    }
}
