package dev.bimo.tallyhopper.neoforge.gametest;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.gametest.TallyHopperGameTests;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(TallyHopper.MOD_ID + "_gametest")
public final class TallyHopperNeoForgeGameTests {

    public TallyHopperNeoForgeGameTests(IEventBus modBus) {
        MeasurementClock.useGameTime();
        modBus.addListener(TallyHopperNeoForgeGameTests::register);
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TallyHopperGameTests.FUNCTIONS.forEach(helper::register));
    }
}
