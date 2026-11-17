package dev.bimo.tallyhopper.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.credit.StoredBacklog;
import dev.bimo.tallyhopper.gui.GuiState;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import dev.bimo.tallyhopper.offline.Backlog;
import dev.bimo.tallyhopper.offline.OfflineWindow;
import dev.bimo.tallyhopper.offline.RateBounds;
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

public final class CreditTests {

    private CreditTests() {}

    static final BlockPos HOPPER = new BlockPos(2, 2, 2);

    private static final int LINE_FLOW_TICKS = 800;

    private static final AtomicLong SESSION_IDS = new AtomicLong(Long.MAX_VALUE);

    static TallyHopperBlockEntity placeTallyHopper(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, TallyHopperContent.block().defaultBlockState().setValue(HopperBlock.FACING, facing));
        TallyHopperBlockEntity hopper = helper.getBlockEntity(pos, TallyHopperBlockEntity.class);
        hopper.saplings().setItem(0, new ItemStack(Items.OAK_SAPLING, 16));
        return hopper;
    }

    static TallyHopperBlockEntity placeRatedHopper(GameTestHelper helper, Item item, long perHour) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.EAST);
        setRate(hopper, item, perHour);
        return hopper;
    }

    static void setRate(TallyHopperBlockEntity hopper, Item item, long perHour) {
        hopper.changeOverrides(measurement -> {
            measurement.setOverride(item, perHour);
            return true;
        });
    }

    static OfflineSession.Current session(GameTestHelper helper, Duration sinceLastTick, Duration away) {
        Instant closed = MeasurementClock.now(helper.getLevel()).plus(sinceLastTick);
        Duration cap = away.compareTo(SessionClock.DEFAULT_MAX_OFFLINE) > 0 ? away : SessionClock.DEFAULT_MAX_OFFLINE;
        return new OfflineSession.Current(
                SESSION_IDS.getAndDecrement(), OfflineWindow.between(closed, closed.plus(away), cap));
    }

    static OfflineSession.Current session(GameTestHelper helper, Duration away) {
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
        TallyHopperBlockEntity hopper = placeRatedHopper(helper, Items.COBBLESTONE, 1000);

        helper.runAfterDelay(1, () -> {
            CreditReport report = hopper.credit(session(helper, Duration.ofHours(3)));

            helper.assertValueEqual(report.earned().getOrDefault(Items.COBBLESTONE, 0L), 3000L, "cobblestone earned");
            helper.assertValueEqual(report.delivered(), 3000L, "delivered");
            helper.assertValueEqual(hopper.ledger().backlog().total(), 0L, "backlog");
            helper.assertValueEqual(
                    count(firstHalf, Items.COBBLESTONE) + count(secondHalf, Items.COBBLESTONE),
                    3000,
                    "cobblestone in the chest");
            helper.assertTrue(count(secondHalf, Items.COBBLESTONE) > 0, "the second half was filled too");
            helper.assertTrue(ItemStack.matches(firstHalf.getItem(0), named), "the named stick is untouched");
            helper.assertTrue(
                    ItemStack.matches(firstHalf.getItem(5), new ItemStack(Items.DIRT, 10)), "the dirt is untouched");
            helper.succeed();
        });
    }

    public static void fullTargetBacklogsAndMinecartDrains(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(3, 2, 2);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        fill(chest, Items.STONE);
        TallyHopperBlockEntity hopper = placeRatedHopper(helper, Items.COBBLESTONE, 200);
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
                    int arrived = count(chest, Items.COBBLESTONE);
                    int vanilla = LINE_FLOW_TICKS / 8;
                    helper.assertTrue(
                            arrived >= vanilla - 5 && arrived <= vanilla + 1,
                            arrived + " items arrived in " + LINE_FLOW_TICKS + " ticks; vanilla speed is " + vanilla);
                })
                .thenSucceed();
    }

    public static void chainedHoppersCreditOnce(GameTestHelper helper) {
        BlockPos upstreamPos = new BlockPos(2, 2, 2);
        BlockPos middle = new BlockPos(3, 2, 2);
        BlockPos downstreamPos = new BlockPos(4, 2, 2);
        BlockPos chest = new BlockPos(5, 2, 2);
        TallyHopperBlockEntity upstream = placeTallyHopper(helper, upstreamPos, Direction.EAST);
        helper.setBlock(middle, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        TallyHopperBlockEntity downstream = placeTallyHopper(helper, downstreamPos, Direction.EAST);
        helper.setBlock(chest, Blocks.CHEST);
        setRate(upstream, Items.COBBLESTONE, 100);
        setRate(downstream, Items.COBBLESTONE, 100);

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(upstream.feedsATallyHopper(), "the upstream hopper should see the one downstream");
            helper.assertFalse(downstream.feedsATallyHopper(), "the downstream hopper feeds a chest");
            GuiState upstreamScreen = upstream.guiState();
            helper.assertTrue(upstreamScreen.isPassthrough(), "the upstream screen doesn't say it is a passthrough");
            helper.assertFalse(upstreamScreen.needsSapling(), "the upstream screen still asks for a sapling");
            helper.assertFalse(downstream.guiState().isPassthrough(), "the downstream screen claims passthrough");

            OfflineSession.Current session = session(helper, Duration.ofHours(1));
            helper.assertTrue(upstream.credit(session).isEmpty(), "the upstream hopper earned something");
            helper.assertValueEqual(downstream.credit(session).delivered(), 100L, "the downstream credit");
            helper.assertValueEqual(
                    count(helper.getBlockEntity(chest, ChestBlockEntity.class), Items.COBBLESTONE),
                    100,
                    "cobblestone in the chest");

            helper.setBlock(downstreamPos, Blocks.AIR);
            helper.assertValueEqual(
                    upstream.credit(session(helper, Duration.ofHours(1))).backlogged(),
                    100L,
                    "credit once the line has one Tally Hopper");
            helper.succeed();
        });
    }

    public static void creditsEachSessionOnce(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        setRate(hopper, Items.COBBLESTONE, 100);

        helper.runAfterDelay(1, () -> {
            OfflineSession.Current session = session(helper, Duration.ofHours(1));
            helper.assertValueEqual(hopper.credit(session).backlogged(), 100L, "first credit");
            helper.assertTrue(!hopper.ledger().needsCredit(session), "the session is marked credited");

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

    public static void hundredDaysAtAMillion(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.CHEST);
        TallyHopperBlockEntity hopper = placeRatedHopper(helper, Items.COBBLESTONE, 1_000_000);
        setRate(hopper, Items.IRON_INGOT, 1_000_000);

        helper.runAfterDelay(1, () -> {
            long started = System.nanoTime();
            CreditReport report = hopper.credit(session(helper, Duration.ofDays(100)));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            long perItem = RateBounds.HOPPER_ITEMS_PER_HOUR / 2 * 24 * 100;
            helper.assertValueEqual(report.earned().getOrDefault(Items.COBBLESTONE, 0L), perItem, "cobblestone earned");
            helper.assertValueEqual(report.delivered(), 27L * 64, "delivered into the chest");
            helper.assertValueEqual(hopper.ledger().backlog().total(), Backlog.DEFAULT_CAP, "backlog");
            helper.assertValueEqual(
                    report.refused(), 2 * perItem - 27L * 64 - Backlog.DEFAULT_CAP, "refused over the cap");
            int bytes =
                    serializedSize(hopper.saveWithoutMetadata(helper.getLevel().registryAccess()));
            helper.assertTrue(bytes < 4096, "the hopper saves in " + bytes + " bytes");
            helper.assertTrue(took.toMillis() < 50, "crediting took " + took);
            helper.succeed();
        });
    }

    public static void breakingKeepsTheBacklog(GameTestHelper helper) {
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        setRate(hopper, Items.COBBLESTONE, 500);

        helper.runAfterDelay(1, () -> hopper.credit(session(helper, Duration.ofHours(1))));
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

    // makeMockServerPlayerInLevel is deprecated for removal, but it is the only way to get a player that
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

    public static void creditStopsAtTheCeiling(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.CHEST);
        TallyHopperBlockEntity hopper = placeRatedHopper(helper, Items.COBBLESTONE, 1_000_000);

        helper.runAfterDelay(1, () -> {
            CreditReport report = hopper.credit(session(helper, Duration.ofHours(1)));

            helper.assertValueEqual(
                    report.earned().getOrDefault(Items.COBBLESTONE, 0L),
                    RateBounds.HOPPER_ITEMS_PER_HOUR,
                    "an hour of credit at the ceiling");
            helper.succeed();
        });
    }

    public static void gameRulesHaveDefaults(GameTestHelper helper) {
        GameRules rules = helper.getLevel().getGameRules();
        helper.assertValueEqual(rules.get(TallyHopperGameRules.MAX_OFFLINE_HOURS), 24, "max offline hours");
        helper.assertValueEqual(rules.get(TallyHopperGameRules.BACKLOG_CAP), 1_000_000, "backlog cap");
        helper.assertValueEqual(rules.get(TallyHopperGameRules.MIN_OBSERVATION_MINUTES), 5, "minutes to calibrate");
        helper.assertValueEqual(rules.get(TallyHopperGameRules.REJOIN_SUMMARY), true, "rejoin summary");
        helper.assertValueEqual(
                (long) rules.get(TallyHopperGameRules.MAX_ITEMS_PER_HOUR),
                RateBounds.HOPPER_ITEMS_PER_HOUR,
                "max items per hour");
        for (Identifier id : TallyHopperGameRules.ALL.keySet()) {
            helper.assertTrue(BuiltInRegistries.GAME_RULE.containsKey(id), id + " is registered");
        }
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
