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

public final class TallyHopperGameRules {

    private static final Map<Identifier, GameRule<?>> RULES = new LinkedHashMap<>();

    public static final GameRule<Integer> MAX_OFFLINE_HOURS =
            integer("max_offline_hours", (int) SessionClock.DEFAULT_MAX_OFFLINE.toHours(), 0, Integer.MAX_VALUE);

    public static final GameRule<Integer> BACKLOG_CAP =
            integer("backlog_cap", (int) Backlog.DEFAULT_CAP, 0, Integer.MAX_VALUE);

    public static final GameRule<Integer> MIN_OBSERVATION_MINUTES =
            integer("min_observation_minutes", (int) RateTracker.DEFAULT_WARM_UP.toMinutes(), 1, (int)
                    RateTracker.WINDOW.toMinutes());

    public static final GameRule<Integer> MAX_ITEMS_PER_HOUR =
            integer("max_items_per_hour", (int) RateBounds.HOPPER_ITEMS_PER_HOUR, 0, Integer.MAX_VALUE);

    public static final GameRule<Boolean> REJOIN_SUMMARY = bool("rejoin_summary", true);

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
