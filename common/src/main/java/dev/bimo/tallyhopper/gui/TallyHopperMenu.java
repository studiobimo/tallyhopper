package dev.bimo.tallyhopper.gui;

import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Tally Hopper's menu: a hopper's five slots, plus the position of the block, so the screen can
 * read what the block entity syncs to the client.
 */
public final class TallyHopperMenu extends AbstractContainerMenu {

    public static final int SLOTS = 5;

    private final Container hopper;
    private final BlockPos pos;

    /** Client side: the slots are filled by the server, so an empty container stands in. */
    public TallyHopperMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, new SimpleContainer(SLOTS), pos);
    }

    public TallyHopperMenu(int containerId, Inventory inventory, Container hopper, BlockPos pos) {
        super(TallyHopperContent.menuType(), containerId);
        checkContainerSize(hopper, SLOTS);
        this.hopper = hopper;
        this.pos = pos;
        hopper.startOpen(inventory.player);

        for (int slot = 0; slot < SLOTS; slot++) {
            addSlot(new Slot(hopper, slot, 44 + slot * 18, 20));
        }
        addStandardInventorySlots(inventory, 8, 98);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return hopper.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        hopper.stopOpen(player);
    }

    // The same rules as a vanilla hopper's menu.
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (slotIndex < SLOTS) {
                if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return moved;
    }
}
