package dev.bimo.tallyhopper.registry;

import dev.bimo.tallyhopper.TallyHopper;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;

/**
 * Lifetime totals on the player's Statistics screen, which is a quiet place to keep them.
 *
 * <p>Each loader calls {@link #register()} once.
 */
public final class TallyHopperStats {

    public static final Identifier ITEMS_CREDITED = id("items_credited");
    public static final Identifier ENERGY_SAVED_WH = id("energy_saved_wh");

    private TallyHopperStats() {}

    /** Registers the stats and gives them their number format, the way vanilla does. */
    public static void register() {
        for (Identifier id : List.of(ITEMS_CREDITED, ENERGY_SAVED_WH)) {
            Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
            Stats.CUSTOM.get(id, StatFormatter.DEFAULT);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, path);
    }
}
