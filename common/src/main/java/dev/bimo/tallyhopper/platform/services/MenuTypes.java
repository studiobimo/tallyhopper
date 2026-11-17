package dev.bimo.tallyhopper.platform.services;

import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.gui.TallyHopperMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;

public interface MenuTypes {

    MenuType<TallyHopperMenu> createMenuType();

    void open(ServerPlayer player, TallyHopperBlockEntity hopper);
}
