package dev.bimo.tallyhopper.session;

import dev.bimo.tallyhopper.config.TallyHopperConfig;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.offline.EnergyEstimate;
import dev.bimo.tallyhopper.offline.EnergyFactors;
import dev.bimo.tallyhopper.offline.OfflineWindow;
import dev.bimo.tallyhopper.offline.SaturatingMath;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import dev.bimo.tallyhopper.registry.TallyHopperGameRules;
import dev.bimo.tallyhopper.registry.TallyHopperStats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one thing a Tally Hopper says on its own: a single chat line, once, when credit was applied.
 *
 * <p>Hoppers credit as their chunks load, so reports pile up for a couple of seconds and are then sent
 * as one grouped line, with the details on hover. The {@code tallyhopper:rejoin_summary} gamerule
 * turns it off.
 */
public final class RejoinSummary {

    /** How long to wait for more hoppers before sending, so one line covers them all. */
    public static final int SETTLE_TICKS = 40;

    /** At most this many hoppers are listed on hover; the rest are counted in the total. */
    private static final int LISTED_HOPPERS = 10;

    private final List<Component> lines = new ArrayList<>();
    private String firstPos = "";
    private int hoppers;
    private long items;
    private long delivered;
    private long backlogged;
    private long refused;
    private int waited;

    /** Adds one hopper's credit. */
    void add(BlockPos pos, CreditReport report) {
        hoppers++;
        long earned = 0;
        for (long count : report.earned().values()) {
            earned = SaturatingMath.add(earned, count);
        }
        items = SaturatingMath.add(items, earned);
        delivered = SaturatingMath.add(delivered, report.delivered());
        backlogged = SaturatingMath.add(backlogged, report.backlogged());
        refused = SaturatingMath.add(refused, report.refused());
        if (hoppers == 1) {
            firstPos = pos.toShortString();
        }
        if (lines.size() < LISTED_HOPPERS) {
            lines.add(Component.translatable(
                    "message.tallyhopper.summary.hopper_line", pos.toShortString(), number(earned)));
        }
        waited = 0;
    }

    boolean isEmpty() {
        return hoppers == 0;
    }

    /** Whether enough ticks have passed since the last credit to send one line for all of them. */
    boolean isSettled() {
        return !isEmpty() && waited++ >= SETTLE_TICKS;
    }

    /** Sends the summary to everyone online, and records their statistics and advancement. */
    void send(MinecraftServer server, OfflineWindow window) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            // Nobody to tell yet; wait for someone to join.
            waited = 0;
            return;
        }
        boolean announce = server.getGameRules().get(TallyHopperGameRules.REJOIN_SUMMARY);
        EnergyEstimate energy = EnergyEstimate.of(window.credited(), TallyHopperConfig.energyFactors());
        Component message = message(window, energy);
        AdvancementHolder advancement = server.getAdvancements().get(TallyHopperContent.SLEEP_MODE_ADVANCEMENT);
        for (ServerPlayer player : players) {
            if (announce) {
                player.sendSystemMessage(message);
            }
            player.awardStat(TallyHopperStats.ITEMS_CREDITED, (int) Math.min(Integer.MAX_VALUE, items));
            player.awardStat(TallyHopperStats.ENERGY_SAVED_WH, (int)
                    Math.min(Integer.MAX_VALUE, Math.round(energy.kilowattHours() * 1000)));
            if (advancement != null) {
                player.getAdvancements().award(advancement, "credited");
            }
        }
        lines.clear();
        hoppers = 0;
        items = 0;
        delivered = 0;
        backlogged = 0;
        refused = 0;
        waited = 0;
    }

    /**
     * Two lines: what was earned, then how it landed. The headline is the only thing a player has to
     * read, so it carries the colour; everything that only matters when something went wrong sits
     * under it in dark grey.
     */
    private Component message(OfflineWindow window, EnergyEstimate energy) {
        Component headline = Component.translatable("message.tallyhopper.summary.items", number(items))
                .withStyle(ChatFormatting.GREEN);
        MutableComponent earned = hoppers == 1
                ? Component.translatable(
                        "message.tallyhopper.summary.one", firstPos, duration(window.credited()), headline)
                : Component.translatable(
                        "message.tallyhopper.summary.many", number(hoppers), duration(window.credited()), headline);
        earned.withStyle(ChatFormatting.GRAY);
        if (hoppers > 1) {
            MutableComponent hover = Component.empty();
            lines.forEach(line -> hover.append(line).append(Component.literal("\n")));
            if (hoppers > lines.size()) {
                hover.append(Component.translatable(
                        "message.tallyhopper.summary.and_more", number(hoppers - (long) lines.size())));
            }
            earned.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(hover)));
        }

        MutableComponent landed =
                Component.translatable("message.tallyhopper.summary.detail", number(delivered), number(backlogged));
        if (window.capped()) {
            landed.append(Component.translatable(
                    "message.tallyhopper.summary.capped",
                    number(window.credited().toHours())));
        }
        if (refused > 0) {
            landed.append(Component.translatable("message.tallyhopper.summary.refused", number(refused)));
        }
        if (TallyHopperConfig.showEnergyEstimate() && energy.kilowattHours() > 0) {
            landed.append(energy(energy));
        }
        landed.withStyle(ChatFormatting.DARK_GRAY);

        // An empty root, so the prefix's colours stay on the prefix instead of bleeding into the rest.
        return Component.empty()
                .append(prefix())
                .append(earned)
                .append(Component.literal("\n"))
                .append(landed);
    }

    /** The mod's one piece of chat branding, reusing the block's own translated name. */
    private static Component prefix() {
        return Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("block.tallyhopper.tally_hopper").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component energy(EnergyEstimate energy) {
        EnergyFactors factors = TallyHopperConfig.energyFactors();
        Component hover = Component.translatable(
                "message.tallyhopper.summary.energy_hover",
                decimals(energy.litersWater(), 0),
                decimals(energy.treeDays(), 0),
                decimals(factors.watts(), 0),
                decimals(factors.gridKilogramsCo2PerKilowattHour(), 3),
                decimals(factors.litersWaterPerKilowattHour(), 1));
        return Component.translatable(
                        "message.tallyhopper.summary.energy",
                        decimals(energy.kilowattHours(), 1),
                        decimals(energy.kilogramsCo2(), 2))
                .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    /** Whole hours and minutes, e.g. {@code 8h 01m}. Minutes are padded here so the text needs no format. */
    private static Component duration(Duration credited) {
        long hours = credited.toHours();
        long minutes = credited.toMinutesPart();
        return hours > 0
                ? Component.translatable(
                        "message.tallyhopper.duration.hours_minutes",
                        number(hours),
                        String.format(Locale.ROOT, "%02d", minutes))
                : Component.translatable("message.tallyhopper.duration.minutes", number(minutes));
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String decimals(double value, int places) {
        return String.format(Locale.ROOT, "%,." + places + "f", value);
    }
}
