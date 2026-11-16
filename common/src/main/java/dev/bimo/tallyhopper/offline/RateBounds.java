package dev.bimo.tallyhopper.offline;

import java.math.BigInteger;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RateBounds {

    public static final long HOPPER_ITEMS_PER_HOUR = 9000;

    private static final BigInteger MILLIS_PER_HOUR =
            BigInteger.valueOf(Duration.ofHours(1).toMillis());

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

    public static <K> BigInteger totalPerHour(Map<K, Rate> rates) {
        BigInteger total = BigInteger.ZERO;
        for (Rate rate : rates.values()) {
            total = total.add(BigInteger.valueOf(rate.items())
                    .multiply(MILLIS_PER_HOUR)
                    .divide(BigInteger.valueOf(rate.perMillis())));
        }
        return total;
    }

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
