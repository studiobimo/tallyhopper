package dev.bimo.tallyhopper.neoforge;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.client.TallyHopperScreen;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = TallyHopper.MOD_ID, value = Dist.CLIENT)
public final class TallyHopperNeoForgeClient {

    private TallyHopperNeoForgeClient() {}

    @SubscribeEvent
    @SuppressWarnings("UnusedMethod")
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(TallyHopperContent.menuType(), TallyHopperScreen::new);
    }
}
