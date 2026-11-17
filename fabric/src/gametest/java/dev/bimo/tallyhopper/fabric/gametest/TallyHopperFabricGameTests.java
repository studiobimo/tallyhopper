package dev.bimo.tallyhopper.fabric.gametest;

import dev.bimo.tallyhopper.gametest.TallyHopperGameTests;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class TallyHopperFabricGameTests implements ModInitializer {

    @Override
    public void onInitialize() {
        MeasurementClock.useGameTime();
        TallyHopperGameTests.FUNCTIONS.forEach(
                (id, function) -> Registry.register(BuiltInRegistries.TEST_FUNCTION, id, function));
    }
}
