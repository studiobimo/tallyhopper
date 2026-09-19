package dev.bimo.tallyhopper.neoforge;

import dev.bimo.tallyhopper.TallyHopper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point; delegates to common code. */
@Mod(TallyHopper.MOD_ID)
public final class TallyHopperNeoForge {

    public TallyHopperNeoForge(IEventBus modBus) {
        TallyHopper.init();
    }
}
