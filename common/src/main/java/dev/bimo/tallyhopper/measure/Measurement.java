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

    public static boolean isCountable(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
    }

    public void tick(Instant now) {
        tracker.tick(now);
    }

    public void recordIntake(Item item, long count, Instant now) {
        tracker.tick(now);
        tracker.record(item, count);
    }

    public Map<Item, Rate> measuredRates() {
        return tracker.rates();
    }

    public void restart() {
        tracker = new RateTracker<>();
    }

    public Duration observed() {
        return tracker.observed();
    }

    public Duration warmUp() {
        return warmUp;
    }

    public void setWarmUp(Duration warmUp) {
        this.warmUp = warmUp;
    }

    public boolean isWarmedUp() {
        return tracker.isWarmedUp(warmUp);
    }

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

    public Map<Item, Rate> provenRates() {
        return RateBounds.weigh(measuredRates(), observed(), RateTracker.WINDOW);
    }

    public Map<Item, Long> overrides() {
        return Collections.unmodifiableMap(overridesPerHour);
    }

    public void setOverride(Item item, long perHour) {
        if (perHour < 0) {
            throw new IllegalArgumentException("perHour must not be negative: " + perHour);
        }
        overridesPerHour.put(item, perHour);
    }

    public boolean clearOverride(Item item) {
        return overridesPerHour.remove(item) != null;
    }

    public int clearOverrides() {
        int removed = overridesPerHour.size();
        overridesPerHour.clear();
        return removed;
    }

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
            TallyHopper.LOG.warn("Discarding an unreadable Tally Hopper measurement", e);
            tracker = new RateTracker<>();
        }
        overridesPerHour.clear();
        input.read(OVERRIDES_KEY, OVERRIDES_CODEC).ifPresent(overridesPerHour::putAll);
    }
}
