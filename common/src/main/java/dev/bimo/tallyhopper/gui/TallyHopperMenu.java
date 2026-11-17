package dev.bimo.tallyhopper.gui;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class TallyHopperMenu extends AbstractContainerMenu {

    public static final int SLOTS = 5;

    public static final int RECALIBRATE_BUTTON = 0;

    private static final Identifier EMPTY_SLOT_SAPLING =
            Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, "container/slot/sapling");

    private static final int SLOTS_X = 80;

    private static final int SLOTS_Y = 48;
    private static final int SAPLING_X = 8;
    private static final int SAPLING_Y = 26;

    private final Container hopper;
    private final Container saplings;
    private final BlockPos pos;

    public TallyHopperMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, new SimpleContainer(SLOTS), new SimpleContainer(1), pos);
    }

    public TallyHopperMenu(int containerId, Inventory inventory, Container hopper, Container saplings, BlockPos pos) {
        super(TallyHopperContent.menuType(), containerId);
        checkContainerSize(hopper, SLOTS);
        checkContainerSize(saplings, 1);
        this.hopper = hopper;
        this.saplings = saplings;
        this.pos = pos;
        hopper.startOpen(inventory.player);

        for (int slot = 0; slot < SLOTS; slot++) {
            addSlot(new Slot(hopper, slot, SLOTS_X + slot * 18, SLOTS_Y));
        }
        addSlot(new Slot(saplings, 0, SAPLING_X, SAPLING_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ItemTags.SAPLINGS);
            }

            @Override
            public Identifier getNoItemIcon() {
                return EMPTY_SLOT_SAPLING;
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != RECALIBRATE_BUTTON || player.level().isClientSide()) {
            return false;
        }
        return player.level().getBlockEntity(pos) instanceof TallyHopperBlockEntity tallyHopper
                && tallyHopper.recalibrate();
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

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            int inventoryStart = SLOTS + 1;
            if (slotIndex < inventoryStart) {
                if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ItemTags.SAPLINGS) && moveItemStackTo(stack, SLOTS, inventoryStart, false)) {
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

    public boolean hasSapling() {
        return !saplings.getItem(0).isEmpty();
    }
}
