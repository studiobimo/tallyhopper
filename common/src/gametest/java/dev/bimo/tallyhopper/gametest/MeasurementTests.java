package dev.bimo.tallyhopper.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bimo.tallyhopper.block.TallyHopperBlock;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.command.TallyHopperCommand;
import dev.bimo.tallyhopper.gui.GuiState;
import dev.bimo.tallyhopper.gui.TallyHopperMenu;
import dev.bimo.tallyhopper.measure.Measurement;
import dev.bimo.tallyhopper.offline.Rate;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
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
import org.jspecify.annotations.Nullable;

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

    /** More than the five slots hold, so some is still waiting after the hopper refills from it. */
    private static final long DRAINING_BACKLOG = 500;

    private static TallyHopperBlockEntity placeTallyHopper(GameTestHelper helper, Direction facing) {
        helper.setBlock(HOPPER, TallyHopperContent.block().defaultBlockState().setValue(HopperBlock.FACING, facing));
        TallyHopperBlockEntity hopper = helper.getBlockEntity(HOPPER, TallyHopperBlockEntity.class);
        // A hopper only watches its farm once a sapling has paid for the run, as a player's would.
        hopper.saplings().setItem(0, new ItemStack(Items.OAK_SAPLING, 16));
        return hopper;
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

    /**
     * A stack a player throws in by hand earns nothing. The drop key and throwing a stack out of a
     * screen both reach {@code Player.drop} with {@code thrownFromHand}, which marks the item entity
     * with its thrower; the hopper still picks it up, but the measurement ignores it.
     */
    public static void thrownItemsNotCounted(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.DOWN);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 standing = helper.absoluteVec(Vec3.atBottomCenterOf(HOPPER.above(3)));
        player.snapTo(standing.x, standing.y, standing.z, 0, 90);

        // The lamp means "this was tallied", so nothing thrown may light it.
        AtomicBoolean throwing = new AtomicBoolean(true);
        AtomicBoolean litForAThrow = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (throwing.get() && helper.getBlockState(HOPPER).getValue(TallyHopperBlock.LIT)) {
                litForAThrow.set(true);
            }
        });

        helper.startSequence()
                .thenExecute(() -> {
                    // The drop key hands the stack to exactly this call.
                    onHopper(helper, player.drop(new ItemStack(Items.DIAMOND, 3), true, Prediction.PREDICTED));

                    // Throwing a stack out of the hopper's own screen goes down the same path.
                    HopperMenu menu = new HopperMenu(1, player.getInventory(), hopper);
                    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 4));
                    menu.clicked(MENU_HOTBAR_START, 1, ContainerInput.THROW, player);
                    thrownNear(helper, player).forEach(item -> onHopper(helper, item));
                })
                .thenWaitUntil(() -> {
                    helper.assertItemEntityNotPresent(Items.DIAMOND);
                    helper.assertValueEqual(hopper.countItem(Items.DIAMOND), 7, "thrown diamonds picked up");
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(hopper.measurement().counted(Items.DIAMOND), 0L, "thrown diamonds counted");
                    helper.assertFalse(litForAThrow.get(), "the lamp flashed for a thrown item");
                    throwing.set(false);
                    // The same hopper still counts what a farm drops into it.
                    helper.spawnItem(Items.EMERALD, HOPPER.above());
                })
                .thenWaitUntil(() -> helper.assertValueEqual(
                        hopper.measurement().counted(Items.EMERALD), 1L, "a farm's drop counted"))
                .thenSucceed();
    }

    /** A counted item lights the observer's lamp for its two-tick pulse, and then the lamp goes dark. */
    public static void countedItemsFlashTheLamp(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.DOWN);
        AtomicBoolean seenLit = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (helper.getBlockState(HOPPER).getValue(TallyHopperBlock.LIT)) {
                seenLit.set(true);
            }
        });
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertBlockProperty(HOPPER, TallyHopperBlock.LIT, false);
                    helper.assertFalse(seenLit.get(), "the lamp lit before anything was counted");
                    helper.spawnItem(Items.STONE, HOPPER.above());
                })
                .thenWaitUntil(
                        () -> helper.assertValueEqual(hopper.measurement().counted(Items.STONE), 1L, "stone counted"))
                .thenIdle(TallyHopperBlock.FLASH_TICKS + 1)
                .thenExecute(() -> {
                    helper.assertTrue(seenLit.get(), "the lamp never lit for a counted item");
                    helper.assertBlockProperty(HOPPER, TallyHopperBlock.LIT, false);
                })
                .thenSucceed();
    }

    /** Puts a thrown item on the hopper, so the test doesn't depend on where a throw happens to land. */
    private static void onHopper(GameTestHelper helper, @Nullable ItemEntity thrown) {
        if (thrown == null) {
            throw helper.assertionException(Component.literal("nothing was thrown"));
        }
        thrown.snapTo(helper.absoluteVec(Vec3.atCenterOf(HOPPER.above())));
        thrown.setDeltaMovement(Vec3.ZERO);
    }

    /** The items a player has just thrown, which spawn at their eye height. */
    private static List<ItemEntity> thrownNear(GameTestHelper helper, Entity thrower) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, thrower.getBoundingBox().inflate(4));
    }

    /** A hopper watches nothing until a sapling pays for the run, and then spends exactly one. */
    public static void calibrationNeedsSapling(GameTestHelper helper) {
        helper.setBlock(
                HOPPER, TallyHopperContent.block().defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        TallyHopperBlockEntity hopper = helper.getBlockEntity(HOPPER, TallyHopperBlockEntity.class);
        helper.startSequence()
                .thenExecute(() -> helper.spawnItem(Items.STONE, HOPPER.above()))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertFalse(hopper.isCalibrationPaid(), "paid for calibration without a sapling");
                    helper.assertTrue(hopper.measurement().observed().isZero(), "watched without a sapling");
                    helper.assertValueEqual(
                            hopper.measurement().counted(Items.STONE), 0L, "stone counted without a sapling");
                    hopper.saplings().setItem(0, new ItemStack(Items.OAK_SAPLING, 2));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(hopper.isCalibrationPaid(), "no sapling was spent");
                    helper.assertValueEqual(hopper.saplings().getItem(0).getCount(), 1, "saplings left");
                    helper.spawnItem(Items.STONE, HOPPER.above());
                })
                .thenWaitUntil(() -> helper.assertValueEqual(
                        hopper.measurement().counted(Items.STONE), 1L, "stone counted once paid for"))
                .thenSucceed();
    }

    /** The padlock on the screen spends a sapling and measures again, and does nothing without one. */
    public static void recalibrateSpendsASapling(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.DOWN);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TallyHopperMenu menu =
                new TallyHopperMenu(1, player.getInventory(), hopper, hopper.saplings(), helper.absolutePos(HOPPER));
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            hopper.saplings().getItem(0).getCount(), 15, "saplings after the first run");
                    helper.assertFalse(hopper.measurement().observed().isZero(), "nothing was watched");
                    helper.assertTrue(
                            menu.clickMenuButton(player, TallyHopperMenu.RECALIBRATE_BUTTON),
                            "the padlock did nothing");
                    helper.assertTrue(hopper.measurement().observed().isZero(), "the measurement was kept");
                    helper.assertValueEqual(
                            hopper.saplings().getItem(0).getCount(), 14, "saplings after recalibrating");
                    hopper.saplings().setItem(0, ItemStack.EMPTY);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertFalse(
                        menu.clickMenuButton(player, TallyHopperMenu.RECALIBRATE_BUTTON),
                        "recalibrated with an empty sapling slot"))
                .thenSucceed();
    }

    /**
     * A hopper with a backlog is not watching its farm, so the padlock is refused and no sapling is
     * spent: pressing it would otherwise buy a run that cannot start, behind a bar stuck at zero.
     */
    public static void recalibrateRefusesWhileDraining(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.DOWN);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TallyHopperMenu menu =
                new TallyHopperMenu(1, player.getInventory(), hopper, hopper.saplings(), helper.absolutePos(HOPPER));
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(hopper.isWatching(), "not watching before any backlog arrives");
                    helper.assertValueEqual(
                            hopper.saplings().getItem(0).getCount(), 15, "saplings after the first run");
                    hopper.ledger().backlog().addAll(Map.of(Items.COBBLESTONE, DRAINING_BACKLOG));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(hopper.isWatching(), "watching while the backlog drains");
                    GuiState state = hopper.guiState();
                    helper.assertTrue(state.backlog() > 0, "backlog left to drain");
                    helper.assertFalse(state.isCalibrating(), "the screen claims to be calibrating");
                    helper.assertTrue(state.isWaitingForBacklog(), "the screen doesn't say what it waits for");
                    helper.assertFalse(
                            menu.clickMenuButton(player, TallyHopperMenu.RECALIBRATE_BUTTON),
                            "the padlock recalibrated while the backlog drains");
                    helper.assertValueEqual(
                            hopper.saplings().getItem(0).getCount(), 15, "saplings after the refused press");
                    helper.assertFalse(hopper.measurement().observed().isZero(), "the measurement was thrown away");
                    hopper.ledger().backlog().take(Items.COBBLESTONE, Long.MAX_VALUE);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(hopper.isWatching(), "not watching once the backlog has drained");
                    helper.assertTrue(
                            menu.clickMenuButton(player, TallyHopperMenu.RECALIBRATE_BUTTON),
                            "the padlock did nothing once the backlog had drained");
                    helper.assertValueEqual(
                            hopper.saplings().getItem(0).getCount(), 14, "saplings after recalibrating");
                    helper.assertTrue(hopper.measurement().observed().isZero(), "the measurement was kept");
                })
                .thenSucceed();
    }

    /** Breaking the hopper spills the saplings, because nothing a player put in may be lost. */
    public static void saplingsDropWhenBroken(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, Direction.DOWN);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertValueEqual(hopper.saplings().getItem(0).getCount(), 15, "saplings before breaking");
                    helper.destroyBlock(HOPPER);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertItemEntityCountIs(Items.OAK_SAPLING, HOPPER, 2.0, 15))
                .thenSucceed();
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
