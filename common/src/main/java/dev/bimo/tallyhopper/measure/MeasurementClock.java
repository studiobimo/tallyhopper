package dev.bimo.tallyhopper.measure;

import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Function;
import net.minecraft.world.level.Level;

/**
 * The time a Tally Hopper measures against: the real-world clock, because offline credit is paid in
 * real-world time.
 *
 * <p>GameTest servers run ticks as fast as they can, so wall-clock rates there would be meaningless.
 * The GameTest mods switch to {@link #useGameTime()}, where one tick is its nominal 50 ms.
 */
public final class MeasurementClock {

    private static final long MILLIS_PER_TICK = 50;

    private static Function<Level, Instant> source = level -> Instant.now();

    private MeasurementClock() {}

    public static Instant now(Level level) {
        return source.apply(level);
    }

    /** Measures in game time instead of real time. For GameTests only. */
    public static void useGameTime() {
        source = level -> Instant.ofEpochMilli(level.getGameTime() * MILLIS_PER_TICK);
    }

    /** Measures against {@code clock}, such as a real clock set back to stage a night away. For tests only. */
    public static void useClock(InstantSource clock) {
        source = level -> clock.instant();
    }
}
