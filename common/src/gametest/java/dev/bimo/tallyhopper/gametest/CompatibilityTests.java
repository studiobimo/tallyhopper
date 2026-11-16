package dev.bimo.tallyhopper.gametest;

import static dev.bimo.tallyhopper.gametest.CreditTests.placeRatedHopper;
import static dev.bimo.tallyhopper.gametest.CreditTests.session;

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

public final class CompatibilityTests {

    private CompatibilityTests() {}

    private static final Identifier DRAWER = Identifier.fromNamespaceAndPath("storagedrawers", "oak_full_drawers_1");

    private static final int CHEST_CAPACITY = 27 * 64;

    private static final BlockPos STORAGE = new BlockPos(3, 2, 2);

    private static Block drawer(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getOptional(DRAWER).orElse(null);
        helper.assertTrue(block != null, DRAWER + " is not in the runtime; the test mod failed to load");
        return Objects.requireNonNull(block);
    }

    private static long offer(GameTestHelper helper, BlockPos pos, Item item, long count) {
        ItemSinks.ItemSink sink = Services.ITEM_SINKS.find(helper.getLevel(), helper.absolutePos(pos), Direction.WEST);
        helper.assertTrue(sink != null, "no storage at " + pos + " through the transfer API");
        return Objects.requireNonNull(sink).insert(item, count);
    }

    public static void fillsAModdedDrawer(GameTestHelper helper) {
        helper.setBlock(STORAGE, drawer(helper));
        TallyHopperBlockEntity hopper = placeRatedHopper(helper, Items.COBBLESTONE, 1000);

        helper.runAfterDelay(1, () -> {
            CreditReport report = hopper.credit(session(helper, Duration.ofHours(2)));

            helper.assertValueEqual(report.earned().getOrDefault(Items.COBBLESTONE, 0L), 2000L, "cobblestone earned");
            helper.assertValueEqual(report.delivered(), 2000L, "delivered into the drawer");
            helper.assertValueEqual(hopper.ledger().backlog().total(), 0L, "backlog");
            helper.assertValueEqual(offer(helper, STORAGE, Items.DIRT, 1), 0L, "the drawer is holding the credit");
            helper.succeed();
        });
    }

    public static void aFullDrawerBacklogsTheRest(GameTestHelper helper) {
        helper.setBlock(STORAGE, drawer(helper));
        TallyHopperBlockEntity hopper = placeRatedHopper(helper, Items.COBBLESTONE, RateBounds.HOPPER_ITEMS_PER_HOUR);

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

    public static void aDrawerKeepsWhatItHolds(GameTestHelper helper) {
        helper.setBlock(STORAGE, drawer(helper));
        TallyHopperBlockEntity hopper = placeRatedHopper(helper, Items.COBBLESTONE, 1000);

        helper.runAfterDelay(1, () -> {
            helper.assertValueEqual(offer(helper, STORAGE, Items.DIRT, 100), 100L, "dirt put in the drawer first");

            CreditReport report = hopper.credit(session(helper, Duration.ofHours(3)));

            helper.assertValueEqual(report.delivered(), 0L, "delivered into a drawer holding dirt");
            helper.assertValueEqual(report.backlogged(), 3000L, "backlogged instead");
            helper.assertValueEqual(offer(helper, STORAGE, Items.DIRT, 1), 1L, "the dirt is untouched");
            helper.assertValueEqual(offer(helper, STORAGE, Items.COBBLESTONE, 1), 0L, "and still refuses cobblestone");
            helper.succeed();
        });
    }
}
