package dev.bimo.tallyhopper.fabric;

import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.gui.TallyHopperMenu;
import dev.bimo.tallyhopper.platform.services.MenuTypes;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Carries the hopper's position with Fabric's extended menu type. */
public final class FabricMenuTypes implements MenuTypes {

    @Override
    public MenuType<TallyHopperMenu> createMenuType() {
        return new ExtendedMenuType<>(TallyHopperMenu::new, BlockPos.STREAM_CODEC);
    }

    @Override
    public void open(ServerPlayer player, TallyHopperBlockEntity hopper) {
        player.openMenu(new ExtendedMenuProvider<BlockPos>() {

            @Override
            public BlockPos getScreenOpeningData(ServerPlayer opening) {
                return hopper.getBlockPos();
            }

            @Override
            public Component getDisplayName() {
                return hopper.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opening) {
                return new TallyHopperMenu(containerId, inventory, hopper, hopper.saplings(), hopper.getBlockPos());
            }
        });
    }
}
