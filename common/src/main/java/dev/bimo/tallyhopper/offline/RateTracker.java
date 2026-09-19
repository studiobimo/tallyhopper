package dev.bimo.tallyhopper.offline;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Measures how many items of each type a hopper takes in per unit of time it was actually running.
 *
 * <p>Counts go into wall-clock-minute buckets. Time only counts as observed while the hopper ticks:
 * a gap between ticks longer than {@link #MAX_TICK_GAP} (a paused game, an unloaded chunk, a
 * closed world) is a pause and is excluded, so the rate is items per <em>active</em> millisecond.
 *
 * <p>The window is the most recent {@link #WINDOW} of observed time, not of wall-clock time. A short
 * session therefore blends into the previous measurement instead of erasing it, and the rate a world
 * closed with is still there when it opens again.
 *
 * @param <K> the item key, which must implement {@code equals} and {@code hashCode}
 */
public final class RateTracker<K> {

    public static final Duration WINDOW = Duration.ofMinutes(60);
    public static final Duration MAX_TICK_GAP = Duration.ofSeconds(1);
    public static final Duration DEFAULT_WARM_UP = Duration.ofMinutes(5);

    /** Bounds memory and save size when a hopper only ticks in short bursts. */
    static final int MAX_BUCKETS = 240;

    private static final long BUCKET_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final long WINDOW_MILLIS = WINDOW.toMillis();
    private static final long MAX_TICK_GAP_MILLIS = MAX_TICK_GAP.toMillis();

    private final ArrayDeque<MutableBucket<K>> buckets = new ArrayDeque<>();
    private long observedMillis;
    private @Nullable Instant lastTick;

    public RateTracker() {}

    /** Restores a tracker from {@link #buckets()}. The first tick after a restore is a pause. */
    public RateTracker(List<Bucket<K>> saved) {
        saved.stream().sorted(Comparator.comparingLong(Bucket::minute)).forEach(bucket -> {
            buckets.addLast(new MutableBucket<>(bucket.minute(), bucket.activeMillis(), bucket.counts()));
            observedMillis = SaturatingMath.add(observedMillis, bucket.activeMillis());
        });
        trim();
    }

    /** Called every time the hopper ticks. */
    public void tick(Instant now) {
        Instant previous = lastTick;
        lastTick = now;
        MutableBucket<K> bucket = bucketFor(now);
        if (previous == null) {
            return;
        }
        long gap = Duration.between(previous, now).toMillis();
        if (gap > 0 && gap <= MAX_TICK_GAP_MILLIS) {
            bucket.activeMillis += gap;
            observedMillis += gap;
            trim();
        }
    }

    /** Counts items that entered through the hopper's intake during the current tick. */
    public void record(K item, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        MutableBucket<K> bucket = buckets.peekLast();
        if (lastTick == null || bucket == null) {
            throw new IllegalStateException("tick() must be called before record()");
        }
        if (count > 0) {
            bucket.counts.merge(item, count, SaturatingMath::add);
        }
    }

    /** How much running time the current rates are based on. */
    public Duration observed() {
        return Duration.ofMillis(observedMillis);
    }

    /** Whether enough time was observed for the rates to be trusted with credit. */
    public boolean isWarmedUp(Duration minObservation) {
        return observed().compareTo(minObservation) >= 0;
    }

    /** The measured rate of each item seen in the window. Empty until any time is observed. */
    public Map<K, Rate> rates() {
        if (observedMillis == 0) {
            return Map.of();
        }
        Map<K, Long> totals = new LinkedHashMap<>();
        for (MutableBucket<K> bucket : buckets) {
            bucket.counts.forEach((item, count) -> totals.merge(item, count, SaturatingMath::add));
        }
        Map<K, Rate> rates = new LinkedHashMap<>();
        totals.forEach((item, count) -> rates.put(item, new Rate(count, observedMillis)));
        return rates;
    }

    /** A snapshot for saving, oldest first. */
    public List<Bucket<K>> buckets() {
        List<Bucket<K>> snapshot = new ArrayList<>(buckets.size());
        for (MutableBucket<K> bucket : buckets) {
            snapshot.add(new Bucket<>(bucket.minute, bucket.activeMillis, bucket.counts));
        }
        return snapshot;
    }

    private MutableBucket<K> bucketFor(Instant now) {
        long minute = Math.floorDiv(now.toEpochMilli(), BUCKET_MILLIS);
        MutableBucket<K> newest = buckets.peekLast();
        // A clock that went backwards keeps filling the newest bucket, so buckets stay in order.
        if (newest != null && newest.minute >= minute) {
            return newest;
        }
        MutableBucket<K> created = new MutableBucket<>(minute, 0, Map.of());
        buckets.addLast(created);
        trim();
        return created;
    }

    /** Drops the oldest buckets that the window no longer needs. */
    private void trim() {
        while (buckets.size() > 1) {
            MutableBucket<K> oldest = buckets.getFirst();
            boolean windowFullWithout = observedMillis - oldest.activeMillis >= WINDOW_MILLIS;
            if (!windowFullWithout && buckets.size() <= MAX_BUCKETS) {
                return;
            }
            buckets.removeFirst();
            observedMillis -= oldest.activeMillis;
        }
    }

    /**
     * One wall-clock minute of measurement.
     *
     * @param minute minutes since the Unix epoch
     * @param activeMillis how long the hopper was ticking during that minute
     * @param counts items taken in during that minute
     */
    public record Bucket<K>(long minute, long activeMillis, Map<K, Long> counts) {

        public Bucket {
            if (activeMillis < 0) {
                throw new IllegalArgumentException("activeMillis must not be negative: " + activeMillis);
            }
            counts = Map.copyOf(counts);
            if (counts.values().stream().anyMatch(count -> count < 0)) {
                throw new IllegalArgumentException("counts must not be negative: " + counts);
            }
        }
    }

    private static final class MutableBucket<K> {

        final long minute;
        final Map<K, Long> counts;
        long activeMillis;

        MutableBucket(long minute, long activeMillis, Map<K, Long> counts) {
            this.minute = minute;
            this.activeMillis = activeMillis;
            this.counts = new HashMap<>(counts);
        }
    }
}
