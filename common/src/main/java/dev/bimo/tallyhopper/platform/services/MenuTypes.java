package dev.bimo.tallyhopper.platform.services;

import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.gui.TallyHopperMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;

/**
 * Opening a screen that needs more than vanilla sends: the Tally Hopper's position, so the screen can
 * read what the block entity syncs. Each loader carries that its own way.
 */
public interface MenuTypes {

    MenuType<TallyHopperMenu> createMenuType();

    /** Opens the screen for {@code player}, sending the hopper's position with it. */
    void open(ServerPlayer player, TallyHopperBlockEntity hopper);
}
