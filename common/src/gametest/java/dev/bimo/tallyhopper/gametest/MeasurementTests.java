package dev.bimo.tallyhopper.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bimo.tallyhopper.block.TallyHopperBlock;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.command.TallyHopperCommand;
import dev.bimo.tallyhopper.measure.Measurement;
import dev.bimo.tallyhopper.offline.Rate;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A Tally Hopper measures what its farm makes, and only that.
 *
 * <p>The GameTest mods measure in game time, one tick being 50 ms, so rates here are per game hour.
 */
public final class MeasurementTests {

    private MeasurementTests() {}

    private static final BlockPos HOPPER = new BlockPos(2, 1, 2);

    /** The dispenser fires once every this many ticks: once a second, 3600 items per hour. */
    private static final int FARM_PERIOD = 20;

    private static final long FARM_RATE = 3600;

    /** Past the five-minute warm-up (6000 ticks), plus room for the clock face to update. */
    private static final int FARM_CHECK_TICK = 6100;

    /** Where a hopper's first hotbar slot sits in a {@link HopperMenu}: 5 hopper slots, 27 inventory. */
    private static final int MENU_HOTBAR_START = 5 + 27;

    private static TallyHopperBlockEntity placeTallyHopper(GameTestHelper helper, Direction facing) {
        helper.setBlock(HOPPER, TallyHopperContent.block().defaultBlockState().setValue(HopperBlock.FACING, facing));
        return helper.getBlockEntity(HOPPER, TallyHopperBlockEntity.class);
    }

    private static void dropStack(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(pos));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack, 0, 0, 0);
        helper.getLevel().addFreshEntity(item);
    }

    /**
     * A dispenser clock drops one cobblestone a second onto a Tally Hopper feeding a chest. After the
     * warm-up, the measured rate is within 5% of 3600/h and the clock face reads ready.
     */
    public static void dispenserFarm(GameTestHelper helper) {
        BlockPos dispenserPos = new BlockPos(2, 3, 2);
        BlockPos trigger = new BlockPos(1, 3, 2);
        helper.setBlock(
                dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.DOWN));
        DispenserBlockEntity dispenser = helper.getBlockEntity(dispenserPos, DispenserBlockEntity.class);
        for (int slot = 0; slot < dispenser.getContainerSize(); slot++) {
            dispenser.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        // An air gap, so the hopper catches dropped items instead of pulling from the dispenser.
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.EAST);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);

        helper.onEachTick(() -> {
            long phase = helper.getTick() % FARM_PERIOD;
            if (phase == 0) {
                helper.setBlock(trigger, Blocks.REDSTONE_BLOCK);
            } else if (phase == FARM_PERIOD / 2) {
                helper.setBlock(trigger, Blocks.AIR);
            }
        });

        helper.runAtTickTime(FARM_CHECK_TICK / 2, () -> {
            helper.assertFalse(hopper.measurement().isWarmedUp(), "warmed up before five minutes");
            helper.assertBlockProperty(HOPPER, TallyHopperBlock.READY, false);
        });
        helper.runAtTickTime(FARM_CHECK_TICK, () -> {
            Measurement measurement = hopper.measurement();
            helper.assertTrue(measurement.isWarmedUp(), "warmed up after " + measurement.observed());
            helper.assertBlockProperty(HOPPER, TallyHopperBlock.READY, true);
            Rate rate = measurement.measuredRates().get(Items.COBBLESTONE);
            helper.assertTrue(rate != null, "cobblestone should be measured, got " + measurement.measuredRates());
            double perHour = rate == null ? 0 : rate.itemsPerHour();
            helper.assertTrue(
                    Math.abs(perHour - FARM_RATE) <= FARM_RATE * 0.05,
                    "measured " + perHour + "/h, expected " + FARM_RATE + "/h ±5%");
            helper.succeed();
        });
    }

    /** Clicking and shift-clicking items in through the GUI counts nothing; a dropped item does count. */
    public static void guiInsertsNotCounted(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.DOWN);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        HopperMenu menu = new HopperMenu(1, player.getInventory(), hopper);

        menu.setCarried(new ItemStack(Items.STONE, 10));
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 7));
        menu.quickMoveStack(player, MENU_HOTBAR_START);

        helper.assertValueEqual(hopper.countItem(Items.STONE), 17, "stone put in through the GUI");
        helper.assertValueEqual(hopper.measurement().counted(Items.STONE), 0L, "stone counted from the GUI");

        // The same hopper still counts what its farm drops into it.
        helper.spawnItem(Items.STONE, HOPPER.above());
        helper.succeedWhen(() -> {
            helper.assertItemEntityNotPresent(Items.STONE);
            helper.assertValueEqual(hopper.measurement().counted(Items.STONE), 1L, "dropped stone counted");
        });
    }

    /** A named item is unique, so only its plain copies are counted. */
    public static void onlyDefaultComponentsCounted(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.DOWN);
        ItemStack named = new ItemStack(Items.DIAMOND, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Heirloom"));
        dropStack(helper, HOPPER.above(), named);
        dropStack(helper, HOPPER.above(), new ItemStack(Items.DIAMOND, 2));

        helper.succeedWhen(() -> {
            helper.assertItemEntityNotPresent(Items.DIAMOND);
            helper.assertValueEqual(hopper.countItem(Items.DIAMOND), 5, "diamonds picked up");
            helper.assertValueEqual(hopper.measurement().counted(Items.DIAMOND), 2L, "plain diamonds counted");
        });
    }

    /**
     * Overrides through {@code /tallyhopper rate}: a player may only lower a rate, an operator may set
     * any rate from anywhere, and a non-zero override makes the hopper ready.
     */
    public static void rateOverrideCommand(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.DOWN);
        BlockPos abs = helper.absolutePos(HOPPER);
        String at = abs.getX() + " " + abs.getY() + " " + abs.getZ();

        // A survival player two blocks above the hopper, looking straight down at it.
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(HOPPER.above(2)));
        player.snapTo(above.x, above.y, above.z, 0, 90);
        CommandSourceStack playerSource = player.createCommandSourceStack()
                .withPermission(PermissionSet.NO_PERMISSIONS)
                .withSuppressedOutput();
        CommandSourceStack operator = helper.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPermission(PermissionSet.ALL_PERMISSIONS);

        assertRefused(
                helper,
                playerSource,
                TallyHopperCommand.NAME + " rate set minecraft:diamond 100",
                "raise above measured");
        run(helper, playerSource, TallyHopperCommand.NAME + " rate set minecraft:diamond 0");
        helper.assertValueEqual(hopper.measurement().overrides(), Map.of(Items.DIAMOND, 0L), "overrides");
        helper.assertBlockProperty(HOPPER, TallyHopperBlock.READY, false);

        run(helper, operator, TallyHopperCommand.NAME + " rate set " + at + " minecraft:emerald 500");
        helper.assertValueEqual(
                hopper.measurement().effectiveRates(),
                Map.of(Items.EMERALD, Rate.perHour(500), Items.DIAMOND, Rate.perHour(0)),
                "effective rates");
        helper.assertBlockProperty(HOPPER, TallyHopperBlock.READY, true);
        helper.assertValueEqual(run(helper, playerSource, TallyHopperCommand.NAME + " info"), 2, "info lines");

        player.snapTo(above.x + 20, above.y, above.z, 0, 90);
        CommandSourceStack farAway = player.createCommandSourceStack()
                .withPermission(PermissionSet.NO_PERMISSIONS)
                .withSuppressedOutput();
        assertRefused(helper, farAway, TallyHopperCommand.NAME + " info " + at, "info from 20 blocks away");
        assertRefused(helper, farAway, TallyHopperCommand.NAME + " rate clear " + at, "clear from 20 blocks away");

        helper.assertValueEqual(
                run(helper, operator, TallyHopperCommand.NAME + " rate clear " + at), 2, "overrides cleared");
        helper.assertValueEqual(hopper.measurement().overrides(), Map.<Item, Long>of(), "overrides");
        helper.assertBlockProperty(HOPPER, TallyHopperBlock.READY, false);
        helper.succeed();
    }

    private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw helper.assertionException(Component.literal("/" + command + " failed: " + e.getMessage()));
        }
    }

    private static void assertRefused(GameTestHelper helper, CommandSourceStack source, String command, String what) {
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException expected) {
            return;
        }
        throw helper.assertionException(Component.literal("/" + command + " should be refused: " + what));
    }
}
