package dev.bimo.tallyhopper.gametest;

import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.conversion.ClockConversion;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Using a clock on a hopper converts it in place; sneak-using converts the end of its chain. */
public final class ConversionTests {

    private ConversionTests() {}

    private static Player playerWithClocks(GameTestHelper helper, boolean sneaking) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CLOCK, 2));
        player.setShiftKeyDown(sneaking);
        return player;
    }

    private static InteractionResult useClock(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        return ClockConversion.onUseBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
    }

    private static void placeHopper(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
    }

    /** Contents, custom name and facing survive; the clock is used up; nothing spills. */
    public static void keepsContentsAndFacing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        placeHopper(helper, pos, Direction.EAST);
        HopperBlockEntity hopper = helper.getBlockEntity(pos, HopperBlockEntity.class);
        Component name = Component.literal("Iron farm");
        hopper.applyComponents(
                DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, name).build(), DataComponentPatch.EMPTY);
        // Applying components also resets the contents, so name the hopper before filling it.
        hopper.setItem(0, new ItemStack(Items.IRON_INGOT, 17));
        hopper.setItem(3, new ItemStack(Items.POPPY, 5));
        Player player = playerWithClocks(helper, false);

        InteractionResult result = useClock(helper, player, pos);

        helper.assertTrue(result instanceof InteractionResult.Success, "conversion should succeed, got " + result);
        helper.assertBlockPresent(TallyHopperContent.block(), pos);
        helper.assertBlockProperty(pos, HopperBlock.FACING, Direction.EAST);
        TallyHopperBlockEntity tally = helper.getBlockEntity(pos, TallyHopperBlockEntity.class);
        helper.assertTrue(
                ItemStack.matches(tally.getItem(0), new ItemStack(Items.IRON_INGOT, 17)), "slot 0 kept its iron");
        helper.assertTrue(
                ItemStack.matches(tally.getItem(3), new ItemStack(Items.POPPY, 5)), "slot 3 kept its poppies");
        helper.assertTrue(
                name.equals(tally.getCustomName()), "custom name should be kept, got " + tally.getCustomName());
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "clocks left");
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);
        helper.assertItemEntityNotPresent(Items.POPPY);
        helper.succeed();
    }

    /** Sneak-using the first of ten chained hoppers converts only the tenth. */
    public static void sneakConvertsChainEnd(GameTestHelper helper) {
        for (int x = 0; x < 10; x++) {
            placeHopper(helper, new BlockPos(1 + x, 1, 2), x < 9 ? Direction.EAST : Direction.DOWN);
        }
        Player player = playerWithClocks(helper, true);

        InteractionResult result = useClock(helper, player, new BlockPos(1, 1, 2));

        helper.assertTrue(result instanceof InteractionResult.Success, "conversion should succeed, got " + result);
        for (int x = 0; x < 9; x++) {
            helper.assertBlockPresent(Blocks.HOPPER, new BlockPos(1 + x, 1, 2));
        }
        helper.assertBlockPresent(TallyHopperContent.block(), new BlockPos(10, 1, 2));
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "clocks left");
        helper.succeed();
    }

    /** A chain that loops has no end: nothing converts and the clock is kept. */
    public static void sneakStopsOnLoop(GameTestHelper helper) {
        placeHopper(helper, new BlockPos(2, 1, 2), Direction.EAST);
        placeHopper(helper, new BlockPos(3, 1, 2), Direction.SOUTH);
        placeHopper(helper, new BlockPos(3, 1, 3), Direction.WEST);
        placeHopper(helper, new BlockPos(2, 1, 3), Direction.NORTH);
        Player player = playerWithClocks(helper, true);

        InteractionResult result = useClock(helper, player, new BlockPos(2, 1, 2));

        helper.assertTrue(result instanceof InteractionResult.Fail, "a loop should be refused, got " + result);
        helper.assertBlockNotPresent(TallyHopperContent.block(), new BlockPos(2, 1, 2));
        helper.assertBlockNotPresent(TallyHopperContent.block(), new BlockPos(3, 1, 2));
        helper.assertBlockNotPresent(TallyHopperContent.block(), new BlockPos(3, 1, 3));
        helper.assertBlockNotPresent(TallyHopperContent.block(), new BlockPos(2, 1, 3));
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "clocks left");
        helper.succeed();
    }
}
