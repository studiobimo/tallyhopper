package dev.bimo.tallyhopper.gametest;

import dev.bimo.tallyhopper.TallyHopper;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * Every GameTest function, keyed by id.
 *
 * <p>Each loader's GameTest mod registers these into the test function registry. The tests
 * themselves are data-driven: {@code data/tallyhopper/test_instance/*.json} names the function,
 * structure and time limit, and vanilla's GameTest server runs every instance it finds.
 */
public final class TallyHopperGameTests {

    public static final Map<Identifier, Consumer<GameTestHelper>> FUNCTIONS = Map.ofEntries(
            Map.entry(id("push_parity"), HopperParityTests::push),
            Map.entry(id("pull_parity"), HopperParityTests::pull),
            Map.entry(id("pickup_parity"), HopperParityTests::pickup),
            Map.entry(id("redstone_lock_parity"), HopperParityTests::redstoneLock),
            Map.entry(id("comparator_parity"), HopperParityTests::comparator),
            Map.entry(id("chain_parity"), HopperParityTests::chain),
            Map.entry(id("convert_keeps_contents"), ConversionTests::keepsContentsAndFacing),
            Map.entry(id("sneak_converts_chain_end"), ConversionTests::sneakConvertsChainEnd),
            Map.entry(id("sneak_stops_on_loop"), ConversionTests::sneakStopsOnLoop),
            Map.entry(id("convert_needs_sapling"), ConversionTests::needsSapling),
            Map.entry(id("convert_refuses_upstream"), ConversionTests::refusesUpstreamOfATallyHopper),
            Map.entry(id("recipe_keeps_clock"), CraftingTests::keepsTheClock),
            Map.entry(id("recipe_has_no_uncraft"), CraftingTests::noOtherRecipes),
            Map.entry(id("dispenser_farm_measured"), MeasurementTests::dispenserFarm),
            Map.entry(id("gui_inserts_not_counted"), MeasurementTests::guiInsertsNotCounted),
            Map.entry(id("thrown_items_not_counted"), MeasurementTests::thrownItemsNotCounted),
            Map.entry(id("only_default_components_counted"), MeasurementTests::onlyDefaultComponentsCounted),
            Map.entry(id("rate_override_command"), MeasurementTests::rateOverrideCommand),
            Map.entry(id("calibration_needs_sapling"), MeasurementTests::calibrationNeedsSapling),
            Map.entry(id("recalibrate_spends_a_sapling"), MeasurementTests::recalibrateSpendsASapling),
            Map.entry(id("recalibrate_refuses_while_draining"), MeasurementTests::recalibrateRefusesWhileDraining),
            Map.entry(id("saplings_drop_when_broken"), MeasurementTests::saplingsDropWhenBroken),
            Map.entry(id("credit_fills_double_chest"), CreditTests::fillsDoubleChest),
            Map.entry(id("credit_backlog_drains_to_minecart"), CreditTests::fullTargetBacklogsAndMinecartDrains),
            Map.entry(id("credit_line_mode_vanilla_speed"), CreditTests::lineModeFlowsAtVanillaSpeed),
            Map.entry(id("credit_once_per_session"), CreditTests::creditsEachSessionOnce),
            Map.entry(id("chained_hoppers_credit_once"), CreditTests::chainedHoppersCreditOnce),
            Map.entry(id("credit_only_running_hoppers"), CreditTests::onlyRunningHoppersAreEligible),
            Map.entry(id("credit_hundred_days"), CreditTests::hundredDaysAtAMillion),
            Map.entry(id("break_keeps_backlog"), CreditTests::breakingKeepsTheBacklog),
            Map.entry(id("creative_break_keeps_backlog"), CreditTests::creativeBreakKeepsTheBacklog),
            Map.entry(id("gamerules_have_defaults"), CreditTests::gameRulesHaveDefaults),
            Map.entry(id("credit_stops_at_the_ceiling"), CreditTests::creditStopsAtTheCeiling),
            Map.entry(id("rejoin_summary_awards"), CreditTests::rejoinSummaryAwardsStatsAndAdvancement),
            Map.entry(id("backlog_tooltip"), CreditTests::backlogShowsInTheTooltip),
            Map.entry(id("modded_drawer_filled"), CompatibilityTests::fillsAModdedDrawer),
            Map.entry(id("modded_drawer_full_backlogs"), CompatibilityTests::aFullDrawerBacklogsTheRest),
            Map.entry(id("modded_drawer_keeps_contents"), CompatibilityTests::aDrawerKeepsWhatItHolds),
            Map.entry(id("late_chunk_own_summary"), ChunkLoadingTests::aLateChunkGetsItsOwnSummary),
            Map.entry(id("sleeping_hopper_earns_nothing"), ChunkLoadingTests::aSleepingHopperEarnsNothingWhenItWakes));

    private TallyHopperGameTests() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, path);
    }
}
