package dev.bimo.tallyhopper.fabric;

import dev.bimo.tallyhopper.client.TallyHopperScreen;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/** Fabric client entry point: hooks up the Tally Hopper's screen. */
public final class TallyHopperFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(TallyHopperContent.menuType(), TallyHopperScreen::new);
    }
}
