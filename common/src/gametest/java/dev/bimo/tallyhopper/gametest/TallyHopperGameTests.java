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
            Map.entry(id("recipe_keeps_clock"), CraftingTests::keepsTheClock),
            Map.entry(id("recipe_has_no_uncraft"), CraftingTests::noOtherRecipes),
            Map.entry(id("dispenser_farm_measured"), MeasurementTests::dispenserFarm),
            Map.entry(id("gui_inserts_not_counted"), MeasurementTests::guiInsertsNotCounted),
            Map.entry(id("only_default_components_counted"), MeasurementTests::onlyDefaultComponentsCounted),
            Map.entry(id("rate_override_command"), MeasurementTests::rateOverrideCommand));

    private TallyHopperGameTests() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, path);
    }
}
