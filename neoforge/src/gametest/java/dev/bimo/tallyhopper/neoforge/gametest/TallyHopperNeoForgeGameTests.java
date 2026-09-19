package dev.bimo.tallyhopper.neoforge.gametest;

import dev.bimo.tallyhopper.gametest.TallyHopperGameTests;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Registers the shared GameTest functions. The test instances come from the data pack. */
@Mod("tallyhopper_gametest")
public final class TallyHopperNeoForgeGameTests {

    public TallyHopperNeoForgeGameTests(IEventBus modBus) {
        modBus.addListener(TallyHopperNeoForgeGameTests::register);
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TallyHopperGameTests.FUNCTIONS.forEach(helper::register));
    }
}
