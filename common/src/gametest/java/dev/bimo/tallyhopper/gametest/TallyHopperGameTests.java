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

    public static final Map<Identifier, Consumer<GameTestHelper>> FUNCTIONS = Map.of(
            id("push_parity"), HopperParityTests::push,
            id("pull_parity"), HopperParityTests::pull,
            id("pickup_parity"), HopperParityTests::pickup,
            id("redstone_lock_parity"), HopperParityTests::redstoneLock,
            id("comparator_parity"), HopperParityTests::comparator,
            id("chain_parity"), HopperParityTests::chain);

    private TallyHopperGameTests() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, path);
    }
}
