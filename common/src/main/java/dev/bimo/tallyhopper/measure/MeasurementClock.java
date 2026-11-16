package dev.bimo.tallyhopper.measure;

import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Function;
import net.minecraft.world.level.Level;

public final class MeasurementClock {

    private static final long MILLIS_PER_TICK = 50;

    private static Function<Level, Instant> source = level -> Instant.now();

    private MeasurementClock() {}

    public static Instant now(Level level) {
        return source.apply(level);
    }

    public static void useGameTime() {
        source = level -> Instant.ofEpochMilli(level.getGameTime() * MILLIS_PER_TICK);
    }

    public static void useClock(InstantSource clock) {
        source = level -> clock.instant();
    }
}
