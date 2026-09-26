package dev.bimo.tallyhopper.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Counts what enters a Tally Hopper, and only what a farm produced.
 *
 * <p>Every vanilla automated insert goes through {@code tryMoveInItem}: hoppers pushing and pulling,
 * item pickup, droppers and crafters. A player clicking items in through the GUI never does, so that
 * one hook counts farm output and ignores hand-fed items.
 *
 * <p>Picking an item up off the ground is the exception: it is automated, so it counts, but a player
 * can also stand over a hopper and throw a stack into it. {@code addItem} is hooked as well to tell
 * those apart.
 */
@Mixin(HopperBlockEntity.class)
@SuppressWarnings("UnusedMethod") // Mixin calls the handlers; Error Prone can't see that.
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

    /**
     * Ignores what a player threw in by hand. The item is still picked up, exactly as vanilla would,
     * so hopper behavior is unchanged; only the measurement looks away.
     *
     * <p>Both pickup paths, {@code suckInItems} and {@code entityInside}, come through here, which
     * makes it the last place an item's provenance is known: once a stack is inside a container,
     * who dropped it is forgotten.
     *
     * <p>Vanilla sets a thrower only for a deliberate throw — the drop key and throwing a stack out
     * of a screen both reach {@code Player.drop} with {@code thrownFromHand}. Blocks mined, mobs
     * killed, items lost on death and an overflowing inventory all leave it unset, so a player's own
     * farming still counts.
     */
    @WrapMethod(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z")
    private static boolean tallyhopper$ignoreThrownItems(
            Container container, ItemEntity entity, Operation<Boolean> original) {
        if (container instanceof TallyHopperBlockEntity tally && entity.getOwner() instanceof Player) {
            return tally.intakeWithoutCounting(() -> original.call(container, entity));
        }
        return original.call(container, entity);
    }
}
