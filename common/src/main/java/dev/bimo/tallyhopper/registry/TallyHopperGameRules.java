package dev.bimo.tallyhopper.registry;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.offline.Backlog;
import dev.bimo.tallyhopper.offline.RateBounds;
import dev.bimo.tallyhopper.offline.RateTracker;
import dev.bimo.tallyhopper.offline.SessionClock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

/**
 * World settings, changed with {@code /gamerule tallyhopper:<name> <value>} or on the world's game
 * rule screen. Each loader registers {@link #ALL} under their ids.
 */
public final class TallyHopperGameRules {

    private static final Map<Identifier, GameRule<?>> RULES = new LinkedHashMap<>();

    /** The most real-world time one closed session can earn; longer gaps are cut and reported. */
    public static final GameRule<Integer> MAX_OFFLINE_HOURS =
            integer("max_offline_hours", (int) SessionClock.DEFAULT_MAX_OFFLINE.toHours(), 0, Integer.MAX_VALUE);

    /** The most items one hopper's backlog holds. Credit beyond it is not created; nothing stored is lost. */
    public static final GameRule<Integer> BACKLOG_CAP =
            integer("backlog_cap", (int) Backlog.DEFAULT_CAP, 0, Integer.MAX_VALUE);

    /**
     * How long a hopper must watch its farm before its measured rate earns credit. At most the
     * 60-minute measuring window, which is all a hopper remembers.
     */
    public static final GameRule<Integer> MIN_OBSERVATION_MINUTES =
            integer("min_observation_minutes", (int) RateTracker.DEFAULT_WARM_UP.toMinutes(), 1, (int)
                    RateTracker.WINDOW.toMinutes());

    /**
     * The most one hopper may credit per hour, across every item. The default is what a hopper can
     * physically move, so no honest farm is touched by it; see {@link RateBounds}.
     */
    public static final GameRule<Integer> MAX_ITEMS_PER_HOUR =
            integer("max_items_per_hour", (int) RateBounds.HOPPER_ITEMS_PER_HOUR, 0, Integer.MAX_VALUE);

    /** Whether players are told in chat what their hoppers earned while the world was closed. */
    public static final GameRule<Boolean> REJOIN_SUMMARY = bool("rejoin_summary", true);

    /** Every rule, by id, for registration. */
    public static final Map<Identifier, GameRule<?>> ALL = Map.copyOf(RULES);

    private TallyHopperGameRules() {}

    private static GameRule<Integer> integer(String name, int defaultValue, int min, int max) {
        return add(
                name,
                new GameRule<>(
                        GameRuleCategory.MISC,
                        GameRuleType.INT,
                        IntegerArgumentType.integer(min, max),
                        GameRuleTypeVisitor::visitInteger,
                        Codec.intRange(min, max),
                        value -> value,
                        defaultValue,
                        FeatureFlagSet.of()));
    }

    private static GameRule<Boolean> bool(String name, boolean defaultValue) {
        return add(
                name,
                new GameRule<>(
                        GameRuleCategory.MISC,
                        GameRuleType.BOOL,
                        BoolArgumentType.bool(),
                        GameRuleTypeVisitor::visitBoolean,
                        Codec.BOOL,
                        value -> value ? 1 : 0,
                        defaultValue,
                        FeatureFlagSet.of()));
    }

    private static <T> GameRule<T> add(String name, GameRule<T> rule) {
        RULES.put(Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, name), rule);
        return rule;
    }
}
