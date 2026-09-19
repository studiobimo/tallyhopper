package dev.bimo.tallyhopper.neoforge;

import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.gui.TallyHopperMenu;
import dev.bimo.tallyhopper.platform.services.MenuTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/** Carries the hopper's position in NeoForge's extra menu data. */
public final class NeoForgeMenuTypes implements MenuTypes {

    @Override
    public MenuType<TallyHopperMenu> createMenuType() {
        return IMenuTypeExtension.create(
                (containerId, inventory, data) -> new TallyHopperMenu(containerId, inventory, data.readBlockPos()));
    }

    @Override
    public void open(ServerPlayer player, TallyHopperBlockEntity hopper) {
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, opening) -> new TallyHopperMenu(
                                containerId, inventory, hopper, hopper.saplings(), hopper.getBlockPos()),
                        hopper.getDisplayName()),
                buffer -> buffer.writeBlockPos(hopper.getBlockPos()));
    }
}
