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

/**
 * The Tally Hopper's menu: a hopper's five slots, the sapling a calibration run costs, and the
 * position of the block, so the screen can read what the block entity syncs.
 */
public final class TallyHopperMenu extends AbstractContainerMenu {

    public static final int SLOTS = 5;

    /** The button the padlock on the screen presses; see {@link #clickMenuButton}. */
    public static final int RECALIBRATE_BUTTON = 0;

    /** The ghost sapling an empty calibration slot shows, the way an empty armour slot shows its piece. */
    private static final Identifier EMPTY_SLOT_SAPLING =
            Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, "container/slot/sapling");

    /** Where the five hopper slots start, and where the sapling slot sits. */
    private static final int SLOTS_X = 80;

    private static final int SLOTS_Y = 48;
    private static final int SAPLING_X = 8;
    private static final int SAPLING_Y = 26;

    private final Container hopper;
    private final Container saplings;
    private final BlockPos pos;

    /** Client side: both containers are filled by the server, so empty ones stand in. */
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

    /**
     * The padlock on the screen. Recalibrating costs the player a sapling and can only lower a rate to
     * what the farm actually produces, so anyone who can open the hopper may press it.
     */
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

    // The same rules as a vanilla hopper's menu, with the sapling slot treated like any other.
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
                // A sapling goes to the calibration slot first, and to the hopper only if that is full.
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

    /** Only for the screen, so it can grey the padlock out when there is nothing to spend. */
    public boolean hasSapling() {
        return !saplings.getItem(0).isEmpty();
    }
}
