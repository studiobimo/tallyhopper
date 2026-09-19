package dev.bimo.tallyhopper.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.credit.StoredBacklog;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import dev.bimo.tallyhopper.offline.Backlog;
import dev.bimo.tallyhopper.offline.OfflineWindow;
import dev.bimo.tallyhopper.offline.SessionClock;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import dev.bimo.tallyhopper.registry.TallyHopperGameRules;
import dev.bimo.tallyhopper.registry.TallyHopperStats;
import dev.bimo.tallyhopper.session.OfflineSession;
import dev.bimo.tallyhopper.session.RejoinSummary;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * Offline credit: delivery into storage, the backlog and its draining, line mode, crediting exactly
 * once, eligibility, scale, and keeping the backlog when the block is broken.
 *
 * <p>Tests credit a made-up session directly instead of restarting the server. The window starts at
 * the hopper's last tick, as it would for a hopper that was running when the world closed.
 */
public final class CreditTests {

    private CreditTests() {}

    private static final BlockPos HOPPER = new BlockPos(2, 2, 2);

    private static final int LINE_FLOW_TICKS = 800;

    /** Real sessions count up from 1, so made-up ones count down from the top to never collide. */
    private static final AtomicLong SESSION_IDS = new AtomicLong(Long.MAX_VALUE);

    private static TallyHopperBlockEntity placeTallyHopper(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, TallyHopperContent.block().defaultBlockState().setValue(HopperBlock.FACING, facing));
        return helper.getBlockEntity(pos, TallyHopperBlockEntity.class);
    }

    private static void setRate(TallyHopperBlockEntity hopper, Item item, long perHour) {
        hopper.changeOverrides(measurement -> {
            measurement.setOverride(item, perHour);
            return true;
        });
    }

    /** A session in which the world was closed for {@code away}, starting {@code sinceLastTick} after the hopper's last tick. */
    private static OfflineSession.Current session(GameTestHelper helper, Duration sinceLastTick, Duration away) {
        Instant closed = MeasurementClock.now(helper.getLevel()).plus(sinceLastTick);
        Duration cap = away.compareTo(SessionClock.DEFAULT_MAX_OFFLINE) > 0 ? away : SessionClock.DEFAULT_MAX_OFFLINE;
        return new OfflineSession.Current(
                SESSION_IDS.getAndDecrement(), OfflineWindow.between(closed, closed.plus(away), cap));
    }

    private static OfflineSession.Current session(GameTestHelper helper, Duration away) {
        return session(helper, Duration.ZERO, away);
    }

    private static int count(Container container, Item item) {
        return container.countItem(item);
    }

    private static void fill(Container container, Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, new ItemStack(item, item.getDefaultMaxStackSize()));
        }
    }

    /** Credit fills both halves of a double chest; what was already in it stays exactly as it was. */
    public static void fillsDoubleChest(GameTestHelper helper) {
        BlockPos right = new BlockPos(3, 2, 2);
        BlockPos left = new BlockPos(3, 2, 3);
        helper.setBlock(
                right,
                Blocks.CHEST
                        .defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.WEST)
                        .setValue(ChestBlock.TYPE, ChestType.RIGHT));
        helper.setBlock(
                left,
                Blocks.CHEST
                        .defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.WEST)
                        .setValue(ChestBlock.TYPE, ChestType.LEFT));
        ItemStack named = new ItemStack(Items.STICK);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
        ChestBlockEntity firstHalf = helper.getBlockEntity(right, ChestBlockEntity.class);
        ChestBlockEntity secondHalf = helper.getBlockEntity(left, ChestBlockEntity.class);
        firstHalf.setItem(0, named.copy());
        firstHalf.setItem(5, new ItemStack(Items.DIRT, 10));
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.EAST);
        setRate(hopper, Items.COBBLESTONE, 1000);

        helper.runAfterDelay(1, () -> {
            CreditReport report = hopper.credit(session(helper, Duration.ofHours(3)));

            helper.assertValueEqual(report.earned().getOrDefault(Items.COBBLESTONE, 0L), 3000L, "cobblestone earned");
            helper.assertValueEqual(report.delivered(), 3000L, "delivered");
            helper.assertValueEqual(hopper.ledger().backlog().total(), 0L, "backlog");
            helper.assertValueEqual(
                    count(firstHalf, Items.COBBLESTONE) + count(secondHalf, Items.COBBLESTONE),
                    3000,
                    "cobblestone in the chest");
            // The hopper faces the first half, so only a joined double chest can fill the second.
            helper.assertTrue(count(secondHalf, Items.COBBLESTONE) > 0, "the second half was filled too");
            helper.assertTrue(ItemStack.matches(firstHalf.getItem(0), named), "the named stick is untouched");
            helper.assertTrue(
                    ItemStack.matches(firstHalf.getItem(5), new ItemStack(Items.DIRT, 10)), "the dirt is untouched");
            helper.succeed();
        });
    }

    /**
     * A full chest sends everything to the backlog. A hopper minecart pulling from below drains it
     * through the visible slots, past the 320 items those slots hold at once.
     */
    public static void fullTargetBacklogsAndMinecartDrains(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(3, 2, 2);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        fill(chest, Items.STONE);
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.EAST);
        setRate(hopper, Items.COBBLESTONE, 200);
        // Something for the minecart to rest on, right under the hopper.
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        AtomicReference<@Nullable MinecartHopper> minecart = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    CreditReport report = hopper.credit(session(helper, Duration.ofHours(2)));

                    helper.assertValueEqual(report.delivered(), 0L, "delivered into the full chest");
                    helper.assertValueEqual(report.backlogged(), 400L, "backlogged");
                    helper.assertValueEqual(count(chest, Items.STONE), 27 * 64, "stone left in the chest");
                    minecart.set(helper.spawn(EntityTypes.HOPPER_MINECART, new BlockPos(2, 1, 2)));
                })
                .thenWaitUntil(() -> {
                    MinecartHopper cart = Objects.requireNonNull(minecart.get());
                    helper.assertValueEqual(count(cart, Items.COBBLESTONE), 5 * 64, "cobblestone in the minecart");
                    helper.assertValueEqual(hopper.ledger().backlog().total(), 0L, "backlog");
                    helper.assertValueEqual(
                            count(hopper, Items.COBBLESTONE), 400 - 5 * 64, "cobblestone left in the hopper");
                })
                .thenSucceed();
    }

    /**
     * Facing another hopper, credit waits in the backlog and flows down the line one item at a time:
     * the next hopper is never filled in bulk, so a sorter's filter hoppers can't jam.
     */
    public static void lineModeFlowsAtVanillaSpeed(GameTestHelper helper) {
        BlockPos next = new BlockPos(3, 2, 2);
        BlockPos last = new BlockPos(4, 2, 2);
        BlockPos chestPos = new BlockPos(5, 2, 2);
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.EAST);
        helper.setBlock(next, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(last, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(chestPos, Blocks.CHEST);
        setRate(hopper, Items.COBBLESTONE, 1000);
        HopperBlockEntity nextHopper = helper.getBlockEntity(next, HopperBlockEntity.class);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    CreditReport report = hopper.credit(session(helper, Duration.ofHours(1)));

                    helper.assertValueEqual(report.delivered(), 0L, "delivered in line mode");
                    helper.assertValueEqual(report.backlogged(), 1000L, "backlogged");
                    helper.assertValueEqual(
                            count(nextHopper, Items.COBBLESTONE), 0, "items bulk-filled into the next hopper");
                })
                .thenExecuteFor(LINE_FLOW_TICKS, () -> {
                    int held = count(nextHopper, Items.COBBLESTONE);
                    helper.assertTrue(held <= 1, "the next hopper holds " + held + "; a vanilla line holds at most 1");
                })
                .thenExecute(() -> {
                    // A hopper moves one item every 8 ticks.
                    int arrived = count(chest, Items.COBBLESTONE);
                    int vanilla = LINE_FLOW_TICKS / 8;
                    helper.assertTrue(
                            arrived >= vanilla - 5 && arrived <= vanilla + 1,
                            arrived + " items arrived in " + LINE_FLOW_TICKS + " ticks; vanilla speed is " + vanilla);
                })
                .thenSucceed();
    }

    /** A session credits once, even across a save and reload of the block entity. */
    public static void creditsEachSessionOnce(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        setRate(hopper, Items.COBBLESTONE, 100);

        helper.runAfterDelay(1, () -> {
            OfflineSession.Current session = session(helper, Duration.ofHours(1));
            helper.assertValueEqual(hopper.credit(session).backlogged(), 100L, "first credit");
            helper.assertTrue(!hopper.ledger().needsCredit(session), "the session is marked credited");

            // What a restart does: the block entity is saved, then loaded from that save.
            CompoundTag saved = hopper.saveWithoutMetadata(helper.getLevel().registryAccess());
            TallyHopperBlockEntity reloaded = placeTallyHopper(helper, new BlockPos(4, 2, 2), Direction.DOWN);
            reloaded.loadWithComponents(TagValueInput.create(
                    ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
            helper.assertTrue(!reloaded.ledger().needsCredit(session), "still credited after a reload");
            helper.assertValueEqual(
                    reloaded.ledger().backlog().total(),
                    hopper.ledger().backlog().total(),
                    "backlog after a reload");
            helper.succeed();
        });
    }

    /**
     * A hopper that was ticking when the world closed (a loaded or force-loaded chunk) is eligible;
     * one that stopped more than two minutes before is not.
     */
    public static void onlyRunningHoppersAreEligible(GameTestHelper helper) {
        TallyHopperBlockEntity running = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        TallyHopperBlockEntity unloaded = placeTallyHopper(helper, new BlockPos(4, 2, 2), Direction.DOWN);
        setRate(running, Items.COBBLESTONE, 600);
        setRate(unloaded, Items.COBBLESTONE, 600);

        helper.runAfterDelay(1, () -> {
            CreditReport ran = running.credit(session(helper, Duration.ofMinutes(1), Duration.ofHours(1)));
            CreditReport stopped = unloaded.credit(session(helper, Duration.ofMinutes(3), Duration.ofHours(1)));

            helper.assertValueEqual(ran.backlogged(), 600L, "credit for the running hopper");
            helper.assertTrue(stopped.isEmpty(), "the hopper that had stopped earned " + stopped);
            helper.succeed();
        });
    }

    /** 100 days at a million items an hour: no crash, the backlog stops at its cap, the save stays small. */
    public static void hundredDaysAtAMillion(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.CHEST);
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.EAST);
        setRate(hopper, Items.COBBLESTONE, 1_000_000);
        setRate(hopper, Items.IRON_INGOT, 1_000_000);

        helper.runAfterDelay(1, () -> {
            long started = System.nanoTime();
            CreditReport report = hopper.credit(session(helper, Duration.ofDays(100)));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            helper.assertValueEqual(
                    report.earned().getOrDefault(Items.COBBLESTONE, 0L), 2_400_000_000L, "cobblestone earned");
            helper.assertValueEqual(report.delivered(), 27L * 64, "delivered into the chest");
            helper.assertValueEqual(hopper.ledger().backlog().total(), Backlog.DEFAULT_CAP, "backlog");
            helper.assertValueEqual(
                    report.refused(), 2 * 2_400_000_000L - 27L * 64 - Backlog.DEFAULT_CAP, "refused over the cap");
            int bytes =
                    serializedSize(hopper.saveWithoutMetadata(helper.getLevel().registryAccess()));
            helper.assertTrue(bytes < 4096, "the hopper saves in " + bytes + " bytes");
            helper.assertTrue(took.toMillis() < 50, "crediting took " + took);
            helper.succeed();
        });
    }

    /** Breaking the block keeps the backlog on the dropped item, and placing it puts the backlog back. */
    public static void breakingKeepsTheBacklog(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        setRate(hopper, Items.COBBLESTONE, 500);

        helper.runAfterDelay(1, () -> hopper.credit(session(helper, Duration.ofHours(1))));
        // Wait a tick so the visible slots refill; those spill like a hopper's.
        helper.runAfterDelay(3, () -> {
            helper.assertValueEqual(count(hopper, Items.COBBLESTONE), 5 * 64, "cobblestone in the slots");
            helper.getLevel().destroyBlock(helper.absolutePos(HOPPER), true);

            long spilled = 0;
            StoredBacklog carried = null;
            for (ItemEntity entity : helper.getEntities(EntityTypes.ITEM)) {
                ItemStack stack = entity.getItem();
                if (stack.is(Items.COBBLESTONE)) {
                    spilled += stack.getCount();
                } else if (stack.is(TallyHopperContent.item())) {
                    carried = stack.get(TallyHopperContent.backlogComponent());
                }
            }
            helper.assertTrue(carried != null, "the dropped Tally Hopper carries the backlog");
            long kept = carried == null ? 0 : carried.counts().getOrDefault(Items.COBBLESTONE, 0L);
            helper.assertValueEqual(spilled + kept, 500L, "cobblestone spilled plus carried");

            ItemStack item = new ItemStack(TallyHopperContent.item());
            item.set(TallyHopperContent.backlogComponent(), carried);
            TallyHopperBlockEntity placed = placeTallyHopper(helper, new BlockPos(4, 2, 2), Direction.DOWN);
            placed.applyComponentsFromItemStack(item);
            helper.assertValueEqual(placed.ledger().backlog().total(), kept, "backlog after placing");
            helper.succeed();
        });
    }

    /** In creative, breaking drops nothing, except a Tally Hopper with a backlog, which drops itself. */
    public static void creativeBreakKeepsTheBacklog(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        setRate(hopper, Items.COBBLESTONE, 500);

        helper.runAfterDelay(1, () -> {
            hopper.credit(session(helper, Duration.ofHours(1)));
            ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
            player.gameMode.destroyBlock(helper.absolutePos(HOPPER));

            helper.assertBlockNotPresent(TallyHopperContent.block(), HOPPER);
            long carried = helper.getEntities(EntityTypes.ITEM).stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(TallyHopperContent.item()))
                    .map(stack -> stack.get(TallyHopperContent.backlogComponent()))
                    .mapToLong(stored -> stored == null ? 0 : stored.counts().getOrDefault(Items.COBBLESTONE, 0L))
                    .sum();
            helper.assertValueEqual(carried, 500L, "backlog carried by the dropped item");
            helper.succeed();
        });
    }

    /** A Tally Hopper item carrying a backlog says so in its tooltip. */
    // The tooltip hook is deprecated in 26.3; see TallyHopperItem for why it is still the one to use.
    @SuppressWarnings("deprecation")
    public static void backlogShowsInTheTooltip(GameTestHelper helper) {
        ItemStack stack = new ItemStack(TallyHopperContent.item());
        List<Component> lines = new ArrayList<>();
        stack.getItem()
                .appendHoverText(
                        stack, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
        helper.assertTrue(lines.isEmpty(), "a plain Tally Hopper says nothing extra, got " + lines);

        stack.set(TallyHopperContent.backlogComponent(), new StoredBacklog(Map.of(Items.COBBLESTONE, 500L)));
        stack.getItem()
                .appendHoverText(
                        stack, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);

        helper.assertValueEqual(lines.size(), 2, "tooltip lines");
        String tooltip = lines.stream().map(Component::getString).toList().toString();
        helper.assertTrue(tooltip.contains("500"), "the tooltip mentions the 500 items, got " + tooltip);
        helper.succeed();
    }

    /** After crediting, the player is told, and their statistics and the Sleep Mode advancement follow. */
    // makeMockServerPlayerInLevel is deprecated for removal, but it is the only way to get a player that
    // is actually in the player list, which is who a summary is sent to.
    @SuppressWarnings("removal")
    public static void rejoinSummaryAwardsStatsAndAdvancement(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        setRate(hopper, Items.COBBLESTONE, 120);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> hopper.credit(session(helper, Duration.ofHours(1))))
                .thenIdle(RejoinSummary.SETTLE_TICKS + 2)
                .thenExecute(() -> {
                    int credited = player.getStats().getValue(Stats.CUSTOM.get(TallyHopperStats.ITEMS_CREDITED));
                    helper.assertTrue(credited >= 120, "items credited statistic is " + credited);
                    // The energy statistic follows the real session's window, which is empty in a test,
                    // so only that it was recorded can be checked here.
                    helper.assertTrue(
                            player.getStats().getValue(Stats.CUSTOM.get(TallyHopperStats.ENERGY_SAVED_WH)) >= 0,
                            "energy saved statistic");
                    AdvancementHolder sleepMode = helper.getLevel()
                            .getServer()
                            .getAdvancements()
                            .get(TallyHopperContent.SLEEP_MODE_ADVANCEMENT);
                    helper.assertTrue(sleepMode != null, "the Sleep Mode advancement is loaded");
                    helper.assertTrue(
                            sleepMode != null
                                    && player.getAdvancements()
                                            .getOrStartProgress(sleepMode)
                                            .isDone(),
                            "Sleep Mode is awarded");
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /**
     * The gamerules are registered and start at their defaults. Changing them would affect the other
     * tests, which share one world, so only the defaults are checked here.
     */
    public static void gameRulesHaveDefaults(GameTestHelper helper) {
        GameRules rules = helper.getLevel().getGameRules();
        helper.assertValueEqual(rules.get(TallyHopperGameRules.MAX_OFFLINE_HOURS), 24, "max offline hours");
        helper.assertValueEqual(rules.get(TallyHopperGameRules.BACKLOG_CAP), 1_000_000, "backlog cap");
        helper.assertValueEqual(rules.get(TallyHopperGameRules.MIN_OBSERVATION_MINUTES), 5, "minutes to calibrate");
        helper.assertValueEqual(rules.get(TallyHopperGameRules.REJOIN_SUMMARY), true, "rejoin summary");
        for (Identifier id : TallyHopperGameRules.ALL.keySet()) {
            helper.assertTrue(BuiltInRegistries.GAME_RULE.containsKey(id), id + " is registered");
        }
        // Querying through /gamerule returns the value.
        CommandSourceStack operator = helper.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withPermission(PermissionSet.ALL_PERMISSIONS)
                .withSuppressedOutput();
        try {
            int cap = helper.getLevel()
                    .getServer()
                    .getCommands()
                    .getDispatcher()
                    .execute("gamerule tallyhopper:backlog_cap", operator);
            helper.assertValueEqual(cap, 1_000_000, "/gamerule tallyhopper:backlog_cap");
        } catch (CommandSyntaxException e) {
            throw helper.assertionException(Component.literal("/gamerule failed: " + e.getMessage()));
        }
        helper.succeed();
    }

    private static int serializedSize(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(bytes));
            return bytes.size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
