package dev.bimo.tallyhopper.fabric;

import dev.bimo.tallyhopper.TallyHopper;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point; delegates to common code. */
public final class TallyHopperFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        TallyHopper.init();
    }
}
