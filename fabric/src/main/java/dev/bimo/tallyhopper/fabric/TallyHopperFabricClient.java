package dev.bimo.tallyhopper.fabric;

import dev.bimo.tallyhopper.client.TallyHopperScreen;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class TallyHopperFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(TallyHopperContent.menuType(), TallyHopperScreen::new);
    }
}
