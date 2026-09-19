package dev.bimo.tallyhopper.fabric;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.command.TallyHopperCommand;
import dev.bimo.tallyhopper.conversion.ClockConversion;
import dev.bimo.tallyhopper.crafting.ShapelessKeepRecipe;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import dev.bimo.tallyhopper.registry.TallyHopperGameRules;
import dev.bimo.tallyhopper.session.OfflineSession;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

/** Fabric entry point; delegates to common code. */
public final class TallyHopperFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        TallyHopper.init();

        TallyHopperGameRules.ALL.forEach((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                TallyHopperContent.BACKLOG_COMPONENT_KEY,
                TallyHopperContent.createBacklogComponent());
        Registry.register(BuiltInRegistries.BLOCK, TallyHopperContent.BLOCK_KEY, TallyHopperContent.createBlock());
        Registry.register(BuiltInRegistries.ITEM, TallyHopperContent.ITEM_KEY, TallyHopperContent.createItem());
        Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                TallyHopperContent.BLOCK_ENTITY_KEY,
                TallyHopperContent.createBlockEntityType());
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                TallyHopperContent.SHAPELESS_KEEP_KEY,
                ShapelessKeepRecipe.SERIALIZER);

        CreativeModeTabEvents.modifyOutputEvent(TallyHopperContent.REDSTONE_BLOCKS_TAB)
                .register(output -> output.insertAfter(Items.HOPPER, TallyHopperContent.item()));

        UseBlockCallback.EVENT.register(ClockConversion::onUseBlock);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, selection) -> TallyHopperCommand.register(dispatcher, context));

        ServerLifecycleEvents.SERVER_STARTED.register(OfflineSession::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(OfflineSession::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(OfflineSession::onServerStopping);
    }
}
