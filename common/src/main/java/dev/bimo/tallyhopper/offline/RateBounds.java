package dev.bimo.tallyhopper.offline;

import java.math.BigInteger;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The two bounds on what a hopper may credit, so a short burst of hand-fed items can't be turned into
 * an endless supply.
 *
 * <p>A measured rate is an extrapolation, and a rate measured over five minutes is a far weaker claim
 * than the same rate measured over an hour. {@link #weigh} scales a rate by how much of the window was
 * actually watched, so a brief burst earns a brief burst's worth.
 *
 * <p>{@link #capTotal} then holds the total to what a hopper can physically move. Everything a Tally
 * Hopper credits had to pass through its five slots at vanilla speed, so no honest farm can be above
 * that ceiling for long; only a window too short to see the truth can be.
 *
 * <p>All scaling is exact: rates stay fractions and are reduced rather than rounded, so a slow farm
 * keeps a small rate instead of being rounded away to nothing.
 */
public final class RateBounds {

    /** A vanilla hopper moves one item every eight ticks: 2.5 a second, 9,000 an hour. */
    public static final long HOPPER_ITEMS_PER_HOUR = 9000;

    private static final BigInteger MILLIS_PER_HOUR =
            BigInteger.valueOf(Duration.ofHours(1).toMillis());

    /** A fraction this wide still fits in the longs a {@link Rate} is made of. */
    private static final int SAFE_BITS = 62;

    private RateBounds() {}

    /**
     * Scales measured rates by {@code observed / window}, the share of the measuring window that was
     * actually watched. A hopper that has watched the whole window is left alone.
     *
     * @param window how much observation a rate needs to be believed in full, {@link RateTracker#WINDOW}
     */
    public static <K> Map<K, Rate> weigh(Map<K, Rate> measured, Duration observed, Duration window) {
        long observedMillis = observed.toMillis();
        long windowMillis = window.toMillis();
        if (windowMillis <= 0 || observedMillis >= windowMillis) {
            return new LinkedHashMap<>(measured);
        }
        if (observedMillis <= 0) {
            return Map.of();
        }
        Map<K, Rate> weighed = new LinkedHashMap<>();
        measured.forEach((item, rate) ->
                weighed.put(item, scale(rate, BigInteger.valueOf(observedMillis), BigInteger.valueOf(windowMillis))));
        return weighed;
    }

    /**
     * Scales every rate down together until their total is at or under {@code maxPerHour}, keeping the
     * mix between items. Rates already under the ceiling are left alone.
     */
    public static <K> Map<K, Rate> capTotal(Map<K, Rate> rates, long maxPerHour) {
        if (maxPerHour < 0) {
            throw new IllegalArgumentException("maxPerHour must not be negative: " + maxPerHour);
        }
        if (maxPerHour == 0) {
            return Map.of();
        }
        BigInteger total = totalPerHour(rates);
        BigInteger max = BigInteger.valueOf(maxPerHour);
        if (total.compareTo(max) <= 0) {
            return new LinkedHashMap<>(rates);
        }
        Map<K, Rate> capped = new LinkedHashMap<>();
        rates.forEach((item, rate) -> capped.put(item, scale(rate, max, total)));
        return capped;
    }

    /** What a set of rates comes to per hour, rounded down per item. */
    public static <K> BigInteger totalPerHour(Map<K, Rate> rates) {
        BigInteger total = BigInteger.ZERO;
        for (Rate rate : rates.values()) {
            total = total.add(BigInteger.valueOf(rate.items())
                    .multiply(MILLIS_PER_HOUR)
                    .divide(BigInteger.valueOf(rate.perMillis())));
        }
        return total;
    }

    /**
     * {@code rate} multiplied by {@code numerator / denominator}, which is never more than one. The
     * fraction is reduced and kept exact whenever it fits back into longs, which it does for any rate a
     * hopper can really see. A fraction too large for that is only reachable by a saturated rate, and
     * falls back to whole items per hour, rounded down.
     */
    private static Rate scale(Rate rate, BigInteger numerator, BigInteger denominator) {
        BigInteger items = BigInteger.valueOf(rate.items()).multiply(numerator);
        BigInteger perMillis = BigInteger.valueOf(rate.perMillis()).multiply(denominator);
        BigInteger common = items.gcd(perMillis);
        if (common.signum() > 0) {
            items = items.divide(common);
            perMillis = perMillis.divide(common);
        }
        if (items.bitLength() <= SAFE_BITS && perMillis.bitLength() <= SAFE_BITS) {
            return new Rate(items.longValueExact(), perMillis.longValueExact());
        }
        BigInteger perHour = items.multiply(MILLIS_PER_HOUR).divide(perMillis);
        return Rate.perHour(perHour.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact());
    }
}
