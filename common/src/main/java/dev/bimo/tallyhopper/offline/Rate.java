package dev.bimo.tallyhopper.offline;

import java.time.Duration;

/**
 * An exact item rate: {@code items} per {@code perMillis} milliseconds.
 *
 * <p>Kept as a fraction rather than a {@code double}, so credit math never drifts.
 */
public record Rate(long items, long perMillis) {

    private static final long MILLIS_PER_HOUR = Duration.ofHours(1).toMillis();

    public Rate {
        if (items < 0) {
            throw new IllegalArgumentException("items must not be negative: " + items);
        }
        if (perMillis <= 0) {
            throw new IllegalArgumentException("perMillis must be positive: " + perMillis);
        }
    }

    /** A rate of {@code items} per hour, the unit players type into overrides. */
    public static Rate perHour(long items) {
        return new Rate(items, MILLIS_PER_HOUR);
    }

    /** For display only. Credit math uses the exact fraction. */
    public double itemsPerHour() {
        return (double) items * MILLIS_PER_HOUR / perMillis;
    }
}
