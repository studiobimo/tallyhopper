package dev.bimo.tallyhopper.offline;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Credited items that didn't fit downstream yet, stored as {@code item → count}.
 *
 * <p>Size is per item <em>type</em>, never per item, so a huge backlog costs a few bytes. The cap is
 * a gameplay limit on the total: credit beyond it is never created, but lowering the cap never
 * deletes what is already stored.
 *
 * @param <K> the item key, which must implement {@code equals} and {@code hashCode}
 */
public final class Backlog<K> {

    public static final long DEFAULT_CAP = 1_000_000;

    private final Map<K, Long> counts = new LinkedHashMap<>();
    private long total;
    private long cap;

    public Backlog(long cap) {
        setCap(cap);
    }

    public Backlog(Map<K, Long> saved, long cap) {
        this(cap);
        saved.forEach((item, count) -> put(item, requireNonNegative(count)));
    }

    public long addAll(Map<K, Long> credit) {
        BigInteger requested = BigInteger.ZERO;
        for (long count : credit.values()) {
            requested = requested.add(BigInteger.valueOf(requireNonNegative(count)));
        }
        long room = Math.max(0, cap - total);
        if (requested.compareTo(BigInteger.valueOf(room)) <= 0) {
            credit.forEach(this::put);
            return 0;
        }
        long granted = 0;
        Map<K, Long> shares = new LinkedHashMap<>();
        for (Map.Entry<K, Long> entry : credit.entrySet()) {
            long share = BigInteger.valueOf(entry.getValue())
                    .multiply(BigInteger.valueOf(room))
                    .divide(requested)
                    .longValueExact();
            shares.put(entry.getKey(), share);
            granted += share;
        }
        for (Map.Entry<K, Long> entry : shares.entrySet()) {
            if (granted == room) {
                break;
            }
            if (entry.getValue() < credit.getOrDefault(entry.getKey(), 0L)) {
                entry.setValue(entry.getValue() + 1);
                granted++;
            }
        }
        shares.forEach(this::put);
        return SaturatingMath.clamp(requested.subtract(BigInteger.valueOf(granted)));
    }

    public long take(K item, long max) {
        requireNonNegative(max);
        long held = get(item);
        long taken = Math.min(held, max);
        if (taken == held) {
            counts.remove(item);
        } else {
            counts.put(item, held - taken);
        }
        total -= taken;
        return taken;
    }

    public @Nullable K peek() {
        return counts.isEmpty() ? null : counts.keySet().iterator().next();
    }

    public long get(K item) {
        return counts.getOrDefault(item, 0L);
    }

    public long total() {
        return total;
    }

    public boolean isEmpty() {
        return total == 0;
    }

    public long cap() {
        return cap;
    }

    public void setCap(long cap) {
        this.cap = requireNonNegative(cap);
    }

    public Map<K, Long> contents() {
        return new LinkedHashMap<>(counts);
    }

    private void put(K item, long count) {
        if (count > 0) {
            counts.merge(item, count, SaturatingMath::add);
            total = SaturatingMath.add(total, count);
        }
    }

    private static long requireNonNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("must not be negative: " + value);
        }
        return value;
    }
}
