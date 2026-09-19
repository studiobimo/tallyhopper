package dev.bimo.tallyhopper.offline;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** Restores a saved backlog. Contents over the cap are kept. */
    public Backlog(Map<K, Long> saved, long cap) {
        this(cap);
        saved.forEach((item, count) -> put(item, requireNonNegative(count)));
    }

    /**
     * Adds credit up to the cap and returns how many items were refused.
     *
     * <p>When everything doesn't fit, the room left is shared in proportion to each item's credit,
     * so one item type can't crowd out the rest.
     */
    public long addAll(Map<K, Long> credit) {
        // Summed exactly: a saturated total would make an over-cap credit look like it fits.
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
        // Rounding down leaves fewer spare items than item types; hand them out one each.
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

    /** Removes up to {@code max} of an item, for refilling a visible slot. Returns how many. */
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

    /** Changes the cap. Items already stored above a lower cap are kept. */
    public void setCap(long cap) {
        this.cap = requireNonNegative(cap);
    }

    /** A snapshot for saving and display, in insertion order. */
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
