package dev.bimo.tallyhopper.neoforge;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.command.TallyHopperCommand;
import dev.bimo.tallyhopper.conversion.ClockConversion;
import dev.bimo.tallyhopper.crafting.ShapelessKeepRecipe;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import dev.bimo.tallyhopper.registry.TallyHopperGameRules;
import dev.bimo.tallyhopper.session.OfflineSession;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/** NeoForge entry point; delegates to common code. */
@Mod(TallyHopper.MOD_ID)
public final class TallyHopperNeoForge {

    public TallyHopperNeoForge(IEventBus modBus) {
        TallyHopper.init();
        modBus.addListener(TallyHopperNeoForge::register);
        modBus.addListener(TallyHopperNeoForge::addToCreativeTab);
        modBus.addListener(TallyHopperNeoForge::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(TallyHopperNeoForge::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(
                RegisterCommandsEvent.class,
                event -> TallyHopperCommand.register(event.getDispatcher(), event.getBuildContext()));
        NeoForge.EVENT_BUS.addListener(
                ServerStartedEvent.class, event -> OfflineSession.onServerStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                ServerTickEvent.Post.class, event -> OfflineSession.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                ServerStoppingEvent.class, event -> OfflineSession.onServerStopping(event.getServer()));
    }

    // NeoForge fires this once per registry, blocks first and items second.
    private static void register(RegisterEvent event) {
        event.register(Registries.GAME_RULE, helper -> TallyHopperGameRules.ALL.forEach(helper::register));
        event.register(
                Registries.DATA_COMPONENT_TYPE,
                helper -> helper.register(
                        TallyHopperContent.BACKLOG_COMPONENT_KEY, TallyHopperContent.createBacklogComponent()));
        event.register(
                Registries.BLOCK,
                helper -> helper.register(TallyHopperContent.BLOCK_KEY, TallyHopperContent.createBlock()));
        event.register(
                Registries.ITEM,
                helper -> helper.register(TallyHopperContent.ITEM_KEY, TallyHopperContent.createItem()));
        event.register(
                Registries.BLOCK_ENTITY_TYPE,
                helper -> helper.register(
                        TallyHopperContent.BLOCK_ENTITY_KEY, TallyHopperContent.createBlockEntityType()));
        event.register(
                Registries.RECIPE_SERIALIZER,
                helper -> helper.register(TallyHopperContent.SHAPELESS_KEEP_KEY, ShapelessKeepRecipe.SERIALIZER));
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result =
                ClockConversion.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        if (!(result instanceof InteractionResult.Pass)) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TallyHopperContent.REDSTONE_BLOCKS_TAB)) {
            event.insertAfter(
                    new ItemStack(Items.HOPPER),
                    new ItemStack(TallyHopperContent.item()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    // NeoForge exposes vanilla hoppers to pipes and other mods' transfer code; do the same for ours.
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                TallyHopperContent.blockEntityType(),
                (hopper, side) -> VanillaContainerWrapper.of(hopper));
    }
}
