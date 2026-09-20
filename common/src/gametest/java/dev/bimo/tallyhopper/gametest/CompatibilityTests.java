package dev.bimo.tallyhopper.gametest;

import static dev.bimo.tallyhopper.gametest.CreditTests.HOPPER;
import static dev.bimo.tallyhopper.gametest.CreditTests.placeTallyHopper;
import static dev.bimo.tallyhopper.gametest.CreditTests.session;
import static dev.bimo.tallyhopper.gametest.CreditTests.setRate;

import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.offline.RateBounds;
import dev.bimo.tallyhopper.platform.Services;
import dev.bimo.tallyhopper.platform.services.ItemSinks;
import java.time.Duration;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Credit reaching storage that isn't vanilla's.
 *
 * <p>The test target is Storage Drawers, loaded into the dev and GameTest runtimes only (see each
 * loader's {@code build.gradle}). It is never on the compile classpath: these tests find its block by
 * id and never name one of its classes, so nothing here knows anything about the mod.
 *
 * <p>A drawer is the useful case precisely because it is nothing like a chest. Its block entity is not
 * a {@link net.minecraft.world.Container}, so a vanilla hopper cannot see it at all and only the
 * loader's transfer API can reach it; one drawer slot holds far more than a stack; and a drawer that
 * already holds something refuses every other item, which is how a modded storage block says "not
 * this". See {@code docs/compatibility.md}.
 *
 * <p>A drawer's contents are read back by offering it another item, which tells us what it is holding
 * without assuming any capacity: an empty drawer takes anything, a drawer takes more of what it
 * already holds, and a full one takes nothing.
 */
public final class CompatibilityTests {

    private CompatibilityTests() {}

    /** A single-slot wooden drawer: one item type, far more than a stack of it. */
    private static final Identifier DRAWER = Identifier.fromNamespaceAndPath("storagedrawers", "oak_full_drawers_1");

    /** What a vanilla single chest holds, as a yardstick for "more than any vanilla container". */
    private static final int CHEST_CAPACITY = 27 * 64;

    private static final BlockPos STORAGE = new BlockPos(3, 2, 2);

    /**
     * The modded storage block, or a failed test. Both loaders' dev runtimes load the mod, so a missing
     * block means the runtime is wrong, not that the test should be skipped.
     */
    private static Block drawer(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getOptional(DRAWER).orElse(null);
        helper.assertTrue(block != null, DRAWER + " is not in the runtime; the test mod failed to load");
        return Objects.requireNonNull(block);
    }

    /** Offers {@code count} of {@code item} to the storage through the same transfer API the hopper uses. */
    private static long offer(GameTestHelper helper, BlockPos pos, Item item, long count) {
        ItemSinks.ItemSink sink = Services.ITEM_SINKS.find(helper.getLevel(), helper.absolutePos(pos), Direction.WEST);
        helper.assertTrue(sink != null, "no storage at " + pos + " through the transfer API");
        return Objects.requireNonNull(sink).insert(item, count);
    }

    /** Credit lands in a modded drawer, thousands of items into a slot no vanilla container has. */
    public static void fillsAModdedDrawer(GameTestHelper helper) {
        helper.setBlock(STORAGE, drawer(helper));
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.EAST);
        setRate(hopper, Items.COBBLESTONE, 1000);

        helper.runAfterDelay(1, () -> {
            CreditReport report = hopper.credit(session(helper, Duration.ofHours(2)));

            helper.assertValueEqual(report.earned().getOrDefault(Items.COBBLESTONE, 0L), 2000L, "cobblestone earned");
            helper.assertValueEqual(report.delivered(), 2000L, "delivered into the drawer");
            helper.assertValueEqual(hopper.ledger().backlog().total(), 0L, "backlog");
            // The drawer was empty and would have taken anything; it now holds cobblestone and says so.
            helper.assertValueEqual(offer(helper, STORAGE, Items.DIRT, 1), 0L, "the drawer is holding the credit");
            helper.succeed();
        });
    }

    /**
     * A modded storage block that fills up refuses the rest, and the rest waits in the backlog, exactly
     * as it does for a full chest.
     */
    public static void aFullDrawerBacklogsTheRest(GameTestHelper helper) {
        helper.setBlock(STORAGE, drawer(helper));
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.EAST);
        setRate(hopper, Items.COBBLESTONE, RateBounds.HOPPER_ITEMS_PER_HOUR);

        helper.runAfterDelay(1, () -> {
            CreditReport report = hopper.credit(session(helper, Duration.ofDays(1)));

            long earned = report.earned().getOrDefault(Items.COBBLESTONE, 0L);
            helper.assertValueEqual(earned, RateBounds.HOPPER_ITEMS_PER_HOUR * 24, "cobblestone earned");
            helper.assertTrue(
                    report.delivered() > CHEST_CAPACITY,
                    "one drawer slot took " + report.delivered() + ", no more than a chest would");
            helper.assertTrue(report.delivered() < earned, "the drawer filled up and stopped accepting");
            helper.assertValueEqual(
                    report.delivered() + report.backlogged() + report.refused(), earned, "every item accounted for");
            helper.assertValueEqual(offer(helper, STORAGE, Items.COBBLESTONE, 1), 0L, "the drawer is full");
            helper.succeed();
        });
    }

    /**
     * What a modded storage block already holds is never touched. A drawer holding dirt refuses
     * cobblestone outright, so the whole credit waits in the backlog and the dirt stays where it is.
     */
    public static void aDrawerKeepsWhatItHolds(GameTestHelper helper) {
        helper.setBlock(STORAGE, drawer(helper));
        TallyHopperBlockEntity hopper = placeTallyHopper(helper, HOPPER, Direction.EAST);
        setRate(hopper, Items.COBBLESTONE, 1000);

        helper.runAfterDelay(1, () -> {
            helper.assertValueEqual(offer(helper, STORAGE, Items.DIRT, 100), 100L, "dirt put in the drawer first");

            CreditReport report = hopper.credit(session(helper, Duration.ofHours(3)));

            helper.assertValueEqual(report.delivered(), 0L, "delivered into a drawer holding dirt");
            helper.assertValueEqual(report.backlogged(), 3000L, "backlogged instead");
            // Still holding dirt, and still with room: the credit neither replaced it nor filled it up.
            helper.assertValueEqual(offer(helper, STORAGE, Items.DIRT, 1), 1L, "the dirt is untouched");
            helper.assertValueEqual(offer(helper, STORAGE, Items.COBBLESTONE, 1), 0L, "and still refuses cobblestone");
            helper.succeed();
        });
    }
}
