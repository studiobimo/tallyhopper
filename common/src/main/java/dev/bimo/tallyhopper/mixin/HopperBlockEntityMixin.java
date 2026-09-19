package dev.bimo.tallyhopper.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Counts what enters a Tally Hopper.
 *
 * <p>Every vanilla automated insert goes through {@code tryMoveInItem}: hoppers pushing and pulling,
 * item pickup, droppers and crafters. A player clicking items in through the GUI never does, so this
 * one hook counts farm output and ignores hand-fed items.
 */
@Mixin(HopperBlockEntity.class)
@SuppressWarnings("UnusedMethod") // Mixin calls the handler; Error Prone can't see that.
abstract class HopperBlockEntityMixin {

    @WrapMethod(method = "tryMoveInItem")
    private static ItemStack tallyhopper$countIntake(
            @Nullable Container from,
            Container container,
            ItemStack stack,
            int slot,
            @Nullable Direction direction,
            Operation<ItemStack> original) {
        if (!(container instanceof TallyHopperBlockEntity tally) || stack.isEmpty()) {
            return original.call(from, container, stack, slot, direction);
        }
        // The stack itself may end up in the hopper, so remember what it was before the move.
        ItemStack before = stack.copy();
        ItemStack left = original.call(from, container, stack, slot, direction);
        int moved = before.getCount() - left.getCount();
        if (moved > 0) {
            tally.recordIntake(before, moved);
        }
        return left;
    }
}
