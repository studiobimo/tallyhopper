package dev.bimo.tallyhopper.fabric.gametest;

import dev.bimo.tallyhopper.gametest.TallyHopperGameTests;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registers the shared GameTest functions. The test instances come from the data pack. */
public final class TallyHopperFabricGameTests implements ModInitializer {

    @Override
    public void onInitialize() {
        // The GameTest server ticks as fast as it can, so measure in game time.
        MeasurementClock.useGameTime();
        TallyHopperGameTests.FUNCTIONS.forEach(
                (id, function) -> Registry.register(BuiltInRegistries.TEST_FUNCTION, id, function));
    }
}
