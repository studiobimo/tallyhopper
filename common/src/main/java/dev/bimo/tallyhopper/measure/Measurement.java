package dev.bimo.tallyhopper.measure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.offline.Rate;
import dev.bimo.tallyhopper.offline.RateTracker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What one Tally Hopper has measured: a {@link RateTracker} keyed by item, plus saving and loading.
 *
 * <p>Only stacks with default components are counted. A randomly enchanted bow or a named item is
 * unique, so extrapolating it would fabricate items that never existed.
 */
public final class Measurement {

    public static final Duration WARM_UP = RateTracker.DEFAULT_WARM_UP;

    private static final String BUCKETS_KEY = "tallyhopper_rate";
    private static final Codec<RateTracker.Bucket<Item>> BUCKET_CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.fieldOf("minute").forGetter(RateTracker.Bucket::minute),
                    Codec.LONG.fieldOf("active_ms").forGetter(RateTracker.Bucket::activeMillis),
                    Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.LONG)
                            .fieldOf("counts")
                            .forGetter(RateTracker.Bucket::counts))
            .apply(i, RateTracker.Bucket::new));

    private RateTracker<Item> tracker = new RateTracker<>();

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

    public Duration observed() {
        return tracker.observed();
    }

    public boolean isWarmedUp() {
        return tracker.isWarmedUp(WARM_UP);
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
    }
}
