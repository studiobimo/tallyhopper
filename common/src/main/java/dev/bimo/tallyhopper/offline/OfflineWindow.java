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

    /** Gaps shorter than this are a quick restart, not time away, and earn nothing. */
    public static final Duration MIN_OFFLINE = Duration.ofSeconds(60);

    /** A hopper must have ticked this close to the final heartbeat to be eligible. */
    public static final Duration ELIGIBILITY_GRACE = Duration.ofMinutes(2);

    public OfflineWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(credited, "credited");
        if (credited.isNegative()) {
            throw new IllegalArgumentException("credited must not be negative: " + credited);
        }
    }

    /**
     * Clamps the gap between two instants.
     *
     * <ul>
     *   <li>A negative gap (the clock went backwards) earns nothing.
     *   <li>A gap under {@link #MIN_OFFLINE} earns nothing.
     *   <li>A gap over {@code maxOffline} is cut to {@code maxOffline} and marked {@link #capped}.
     * </ul>
     */
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

    /** A window that credits nothing, used for a world's very first session. */
    public static OfflineWindow none(Instant at) {
        return new OfflineWindow(at, at, Duration.ZERO, false);
    }

    /** The unclamped gap, which is negative if the clock went backwards. */
    public Duration raw() {
        return Duration.between(from, to);
    }

    /**
     * Whether a hopper that last ticked at {@code lastTick} was running when the world closed, so
     * its chunk was loaded and it earns credit for this window.
     */
    public boolean isEligible(Instant lastTick) {
        return !lastTick.isBefore(from.minus(ELIGIBILITY_GRACE));
    }
}
