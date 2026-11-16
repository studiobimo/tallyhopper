package dev.bimo.tallyhopper.offline;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The real-world time a world spent closed, from its last heartbeat to the moment it started again.
 *
 * @param from the last heartbeat of the previous session
 * @param to when the new session started
 * @param credited how much of the gap earns credit, after clamping
 * @param capped whether the gap was longer than the cap, so the player should be told
 */
public record OfflineWindow(Instant from, Instant to, Duration credited, boolean capped) {

    public static final Duration MIN_OFFLINE = Duration.ofSeconds(60);

    public static final Duration ELIGIBILITY_GRACE = Duration.ofMinutes(2);

    public OfflineWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(credited, "credited");
        if (credited.isNegative()) {
            throw new IllegalArgumentException("credited must not be negative: " + credited);
        }
    }

    public static OfflineWindow between(Instant from, Instant to, Duration maxOffline) {
        if (maxOffline.isNegative()) {
            throw new IllegalArgumentException("maxOffline must not be negative: " + maxOffline);
        }
        Duration raw = Duration.between(from, to);
        if (raw.compareTo(MIN_OFFLINE) < 0) {
            return new OfflineWindow(from, to, Duration.ZERO, false);
        }
        if (raw.compareTo(maxOffline) > 0) {
            return new OfflineWindow(from, to, maxOffline, true);
        }
        return new OfflineWindow(from, to, raw, false);
    }

    public static OfflineWindow none(Instant at) {
        return new OfflineWindow(at, at, Duration.ZERO, false);
    }

    public Duration raw() {
        return Duration.between(from, to);
    }

    public boolean isEligible(Instant lastTick) {
        return !lastTick.isBefore(from.minus(ELIGIBILITY_GRACE));
    }

    public Duration creditFor(Instant lastTick) {
        if (credited.isZero() || !isEligible(lastTick)) {
            return Duration.ZERO;
        }
        Duration own = Duration.between(lastTick.isAfter(from) ? lastTick : from, to);
        if (own.isNegative()) {
            return Duration.ZERO;
        }
        return own.compareTo(credited) < 0 ? own : credited;
    }
}
