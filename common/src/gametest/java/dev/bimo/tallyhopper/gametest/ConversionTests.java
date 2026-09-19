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

/**
 * Using a clock on a hopper converts it in place for one sapling; sneak-using converts the end of
 * its chain. The clock is always kept.
 */
public final class ConversionTests {

    private ConversionTests() {}

    private static final int SAPLINGS = 3;

    /** A survival player holding two clocks, with {@code saplings} oak saplings in the inventory. */
    private static Player player(GameTestHelper helper, boolean sneaking, int saplings) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CLOCK, 2));
        if (saplings > 0) {
            player.getInventory().add(new ItemStack(Items.OAK_SAPLING, saplings));
        }
        player.setShiftKeyDown(sneaking);
        return player;
    }

    private static int saplings(Player player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.OAK_SAPLING)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void assertPaid(GameTestHelper helper, Player player, int saplingsUsed) {
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "clocks kept");
        helper.assertValueEqual(saplings(player), SAPLINGS - saplingsUsed, "saplings left");
    }

    private static InteractionResult useClock(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        return ClockConversion.onUseBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
    }

    private static void placeHopper(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
    }

    /** Contents, custom name and facing survive; one sapling is used; nothing spills. */
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
        Player player = player(helper, false, SAPLINGS);

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
        assertPaid(helper, player, 1);
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);
        helper.assertItemEntityNotPresent(Items.POPPY);
        helper.succeed();
    }

    /** Sneak-using the first of ten chained hoppers converts only the tenth. */
    public static void sneakConvertsChainEnd(GameTestHelper helper) {
        for (int x = 0; x < 10; x++) {
            placeHopper(helper, new BlockPos(1 + x, 1, 2), x < 9 ? Direction.EAST : Direction.DOWN);
        }
        Player player = player(helper, true, SAPLINGS);

        InteractionResult result = useClock(helper, player, new BlockPos(1, 1, 2));

        helper.assertTrue(result instanceof InteractionResult.Success, "conversion should succeed, got " + result);
        for (int x = 0; x < 9; x++) {
            helper.assertBlockPresent(Blocks.HOPPER, new BlockPos(1 + x, 1, 2));
        }
        helper.assertBlockPresent(TallyHopperContent.block(), new BlockPos(10, 1, 2));
        assertPaid(helper, player, 1);
        helper.succeed();
    }

    /** A chain that loops has no end: nothing converts and nothing is used. */
    public static void sneakStopsOnLoop(GameTestHelper helper) {
        placeHopper(helper, new BlockPos(2, 1, 2), Direction.EAST);
        placeHopper(helper, new BlockPos(3, 1, 2), Direction.SOUTH);
        placeHopper(helper, new BlockPos(3, 1, 3), Direction.WEST);
        placeHopper(helper, new BlockPos(2, 1, 3), Direction.NORTH);
        Player player = player(helper, true, SAPLINGS);

        InteractionResult result = useClock(helper, player, new BlockPos(2, 1, 2));

        helper.assertTrue(result instanceof InteractionResult.Fail, "a loop should be refused, got " + result);
        helper.assertBlockNotPresent(TallyHopperContent.block(), new BlockPos(2, 1, 2));
        helper.assertBlockNotPresent(TallyHopperContent.block(), new BlockPos(3, 1, 2));
        helper.assertBlockNotPresent(TallyHopperContent.block(), new BlockPos(3, 1, 3));
        helper.assertBlockNotPresent(TallyHopperContent.block(), new BlockPos(2, 1, 3));
        assertPaid(helper, player, 0);
        helper.succeed();
    }

    /** Without a sapling nothing converts. */
    public static void needsSapling(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        placeHopper(helper, pos, Direction.DOWN);
        Player player = player(helper, false, 0);

        InteractionResult result = useClock(helper, player, pos);

        helper.assertTrue(result instanceof InteractionResult.Fail, "conversion should be refused, got " + result);
        helper.assertBlockPresent(Blocks.HOPPER, pos);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "clocks kept");
        helper.succeed();
    }
}
