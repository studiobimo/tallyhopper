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

@Mixin(HopperBlockEntity.class)
@SuppressWarnings("UnusedMethod")
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
        ItemStack before = stack.copy();
        ItemStack left = original.call(from, container, stack, slot, direction);
        int moved = before.getCount() - left.getCount();
        if (moved > 0) {
            tally.recordIntake(before, moved);
        }
        return left;
    }

    @WrapMethod(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z")
    private static boolean tallyhopper$ignoreThrownItems(
            Container container, ItemEntity entity, Operation<Boolean> original) {
        if (container instanceof TallyHopperBlockEntity tally && entity.getOwner() instanceof Player) {
            return tally.intakeWithoutCounting(() -> original.call(container, entity));
        }
        return original.call(container, entity);
    }
}
