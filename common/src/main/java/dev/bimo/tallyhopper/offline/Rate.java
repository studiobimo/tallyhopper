package dev.bimo.tallyhopper.offline;

import java.time.Duration;

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

    public static Rate perHour(long items) {
        return new Rate(items, MILLIS_PER_HOUR);
    }

    public double itemsPerHour() {
        return (double) items * MILLIS_PER_HOUR / perMillis;
    }
}
