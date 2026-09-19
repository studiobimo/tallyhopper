package dev.bimo.tallyhopper.neoforge;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.client.TallyHopperScreen;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** NeoForge client setup: hooks up the Tally Hopper's screen. */
@EventBusSubscriber(modid = TallyHopper.MOD_ID, value = Dist.CLIENT)
public final class TallyHopperNeoForgeClient {

    private TallyHopperNeoForgeClient() {}

    @SubscribeEvent
    @SuppressWarnings("UnusedMethod") // The event bus calls it; Error Prone can't see that.
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(TallyHopperContent.menuType(), TallyHopperScreen::new);
    }
}
