package dev.bimo.tallyhopper.gametest;

import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Proves a Tally Hopper behaves exactly like a vanilla hopper.
 *
 * <p>Each test builds the same rig twice, side by side: once around a vanilla hopper and once around
 * a Tally Hopper. Every tick it compares what both rigs report, so any difference in timing, not
 * just in the final result, fails the test.
 */
public final class HopperParityTests {

    private HopperParityTests() {}

    /** One copy of a rig. {@code tested} is the hopper block under test. */
    record Rig(GameTestHelper helper, int dx, Block tested) {

        BlockPos pos(int x, int y, int z) {
            return new BlockPos(1 + dx + x, y, 1 + z);
        }

        void hopper(BlockPos pos, Direction facing) {
            placeHopper(pos, tested, facing);
        }

        void vanillaHopper(BlockPos pos, Direction facing) {
            placeHopper(pos, Blocks.HOPPER, facing);
        }

        private void placeHopper(BlockPos pos, Block block, Direction facing) {
            helper.setBlock(pos, block.defaultBlockState().setValue(HopperBlock.FACING, facing));
        }

        void fill(BlockPos pos, int count) {
            container(pos).setItem(0, new ItemStack(Items.IRON_INGOT, count));
        }

        int count(BlockPos pos) {
            Container container = container(pos);
            int total = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                total += container.getItem(slot).getCount();
            }
            return total;
        }

        Container container(BlockPos pos) {
            return helper.getBlockEntity(pos, BaseContainerBlockEntity.class);
        }
    }

    /**
     * Builds {@code setup} around a vanilla hopper and a Tally Hopper, then checks for {@code ticks}
     * ticks that {@code observe} reports the same thing for both, and finally runs {@code done} on
     * the Tally Hopper rig.
     */
    static void sideBySide(
            GameTestHelper helper,
            int ticks,
            Consumer<Rig> setup,
            Function<Rig, List<Object>> observe,
            Consumer<Rig> done) {
        Rig vanilla = new Rig(helper, 0, Blocks.HOPPER);
        Rig tally = new Rig(helper, 7, TallyHopperContent.block());
        setup.accept(vanilla);
        setup.accept(tally);
        helper.startSequence()
                .thenExecuteFor(ticks, () -> {
                    List<Object> expected = observe.apply(vanilla);
                    List<Object> actual = observe.apply(tally);
                    helper.assertTrue(
                            Objects.equals(expected, actual),
                            "Tally Hopper diverged from vanilla: expected " + expected + ", got " + actual);
                })
                .thenExecute(() -> done.accept(tally))
                .thenSucceed();
    }

    /** A full hopper pushes one item every 8 ticks into the chest it faces. */
    public static void push(GameTestHelper helper) {
        sideBySide(
                helper,
                100,
                rig -> {
                    rig.helper().setBlock(rig.pos(0, 1, 0), Blocks.CHEST);
                    rig.hopper(rig.pos(0, 2, 0), Direction.DOWN);
                    rig.fill(rig.pos(0, 2, 0), 10);
                },
                rig -> List.of(rig.count(rig.pos(0, 2, 0)), rig.count(rig.pos(0, 1, 0))),
                rig -> rig.helper().assertValueEqual(rig.count(rig.pos(0, 1, 0)), 10, "items in chest"));
    }

    /** A hopper pulls from the container above it. */
    public static void pull(GameTestHelper helper) {
        sideBySide(
                helper,
                100,
                rig -> {
                    rig.hopper(rig.pos(0, 1, 0), Direction.DOWN);
                    rig.helper().setBlock(rig.pos(0, 2, 0), Blocks.CHEST);
                    rig.fill(rig.pos(0, 2, 0), 10);
                },
                rig -> List.of(rig.count(rig.pos(0, 2, 0)), rig.count(rig.pos(0, 1, 0))),
                rig -> rig.helper().assertValueEqual(rig.count(rig.pos(0, 1, 0)), 10, "items in hopper"));
    }

    /** A hopper picks up item entities that land on it. */
    public static void pickup(GameTestHelper helper) {
        sideBySide(
                helper,
                60,
                rig -> {
                    rig.hopper(rig.pos(0, 1, 0), Direction.DOWN);
                    BlockPos above = rig.pos(0, 3, 0);
                    for (int i = 0; i < 3; i++) {
                        rig.helper()
                                .spawnItem(
                                        Items.IRON_INGOT,
                                        new Vec3(above.getX() + 0.5, above.getY(), above.getZ() + 0.5));
                    }
                },
                rig -> List.of(rig.count(rig.pos(0, 1, 0))),
                rig -> rig.helper().assertValueEqual(rig.count(rig.pos(0, 1, 0)), 3, "items picked up"));
    }

    /** A powered hopper is locked: it neither pushes nor pulls. */
    public static void redstoneLock(GameTestHelper helper) {
        sideBySide(
                helper,
                40,
                rig -> {
                    rig.helper().setBlock(rig.pos(0, 1, 0), Blocks.CHEST);
                    rig.helper().setBlock(rig.pos(1, 2, 0), Blocks.REDSTONE_BLOCK);
                    rig.hopper(rig.pos(0, 2, 0), Direction.DOWN);
                    rig.fill(rig.pos(0, 2, 0), 5);
                },
                rig -> List.of(
                        rig.count(rig.pos(0, 2, 0)),
                        rig.count(rig.pos(0, 1, 0)),
                        rig.helper().getBlockState(rig.pos(0, 2, 0)).getValue(HopperBlock.ENABLED)),
                rig -> rig.helper().assertValueEqual(rig.count(rig.pos(0, 1, 0)), 0, "items pushed while locked"));
    }

    /** A comparator reads the same signal from both hoppers while they empty. */
    public static void comparator(GameTestHelper helper) {
        sideBySide(
                helper,
                100,
                rig -> {
                    rig.helper().setBlock(rig.pos(0, 1, 0), Blocks.CHEST);
                    rig.helper().setBlock(rig.pos(1, 1, 0), Blocks.SMOOTH_STONE);
                    rig.hopper(rig.pos(0, 2, 0), Direction.DOWN);
                    // A comparator's FACING points at its input.
                    BlockState comparator =
                            Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST);
                    rig.helper().setBlock(rig.pos(1, 2, 0), comparator);
                    rig.fill(rig.pos(0, 2, 0), 64);
                },
                rig -> List.of(rig.helper()
                        .getBlockEntity(rig.pos(1, 2, 0), ComparatorBlockEntity.class)
                        .getOutputSignal()),
                rig -> rig.helper()
                        .assertTrue(
                                rig.helper()
                                                .getBlockEntity(rig.pos(1, 2, 0), ComparatorBlockEntity.class)
                                                .getOutputSignal()
                                        > 0,
                                "comparator should still read the partly emptied hopper"));
    }

    /**
     * Vanilla hopper → hopper under test → vanilla hopper → chest. Hoppers hand items to each other
     * with a cooldown that depends on {@code instanceof HopperBlockEntity}; this catches any break.
     */
    public static void chain(GameTestHelper helper) {
        sideBySide(
                helper,
                160,
                rig -> {
                    rig.helper().setBlock(rig.pos(2, 1, 0), Blocks.CHEST);
                    rig.vanillaHopper(rig.pos(2, 2, 0), Direction.DOWN);
                    rig.hopper(rig.pos(1, 2, 0), Direction.EAST);
                    rig.vanillaHopper(rig.pos(0, 2, 0), Direction.EAST);
                    rig.fill(rig.pos(0, 2, 0), 10);
                },
                rig -> List.of(
                        rig.count(rig.pos(0, 2, 0)),
                        rig.count(rig.pos(1, 2, 0)),
                        rig.count(rig.pos(2, 2, 0)),
                        rig.count(rig.pos(2, 1, 0))),
                rig -> rig.helper().assertValueEqual(rig.count(rig.pos(2, 1, 0)), 10, "items in chest"));
    }
}
