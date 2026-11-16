package dev.bimo.tallyhopper.offline;

import java.math.BigInteger;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns rates and offline time into whole items, carrying fractions of an item between sessions.
 *
 * <p>A farm making 1 item per hour that is closed for 30 minutes twice earns 1 item, not 0. The
 * carry is kept in millionths of an item and always rounds down, so credit can come up short by a
 * millionth of an item per session but is never created from rounding.
 *
 * @param <K> the item key, which must implement {@code equals} and {@code hashCode}
 */
public final class OfflineCredit<K> {

    static final long MICROS_PER_ITEM = 1_000_000;

    private static final BigInteger MICROS = BigInteger.valueOf(MICROS_PER_ITEM);
    private static final BigInteger MILLIS_PER_SECOND = BigInteger.valueOf(1000);

    private final Map<K, Long> carryMicros = new LinkedHashMap<>();

    public OfflineCredit() {}

    public OfflineCredit(Map<K, Long> savedCarry) {
        savedCarry.forEach((item, micros) -> {
            if (micros < 0 || micros >= MICROS_PER_ITEM) {
                throw new IllegalArgumentException("carry must be under one item: " + micros);
            }
            if (micros > 0) {
                carryMicros.put(item, micros);
            }
        });
    }

    public static <K> Map<K, Rate> effectiveRates(Map<K, Rate> measured, boolean warmedUp, Map<K, Rate> overrides) {
        Map<K, Rate> rates = new LinkedHashMap<>();
        if (warmedUp) {
            rates.putAll(measured);
        }
        rates.putAll(overrides);
        return rates;
    }

    public Map<K, Long> accrue(Map<K, Rate> rates, Duration elapsed) {
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative: " + elapsed);
        }
        BigInteger millis = BigInteger.valueOf(elapsed.toSeconds())
                .multiply(MILLIS_PER_SECOND)
                .add(BigInteger.valueOf(elapsed.toMillisPart()));
        Map<K, Long> earned = new LinkedHashMap<>();
        rates.forEach((item, rate) -> {
            BigInteger micros = BigInteger.valueOf(rate.items())
                    .multiply(millis)
                    .multiply(MICROS)
                    .divide(BigInteger.valueOf(rate.perMillis()))
                    .add(BigInteger.valueOf(carryMicros.getOrDefault(item, 0L)));
            BigInteger[] wholeAndCarry = micros.divideAndRemainder(MICROS);
            long whole = SaturatingMath.clamp(wholeAndCarry[0]);
            long carry = whole == Long.MAX_VALUE ? 0 : wholeAndCarry[1].longValueExact();
            if (carry > 0) {
                carryMicros.put(item, carry);
            } else {
                carryMicros.remove(item);
            }
            if (whole > 0) {
                earned.put(item, whole);
            }
        });
        return earned;
    }

    public Map<K, Long> carry() {
        return new LinkedHashMap<>(carryMicros);
    }
}
