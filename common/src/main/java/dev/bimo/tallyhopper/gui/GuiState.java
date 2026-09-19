package dev.bimo.tallyhopper.gui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bimo.tallyhopper.credit.Ledger;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

/**
 * What the screen shows, synced from the block entity.
 *
 * <p>Only this small summary is sent, never the hopper's minute-by-minute measurement, which is far
 * larger and of no use to the screen.
 *
 * @param ratesPerHour what each item would earn per hour, measured or overridden
 * @param overridden which of those rates a player set by hand
 * @param observedSeconds how long the hopper has watched its farm
 * @param warmUpSeconds how long it must watch before its measured rate counts
 * @param ready whether it would earn credit now
 * @param backlog items waiting to be handed out
 * @param lastCredit items credited the last time the world reopened
 * @param terminal whether it fills storage directly, rather than feeding a line of hoppers
 * @param saplings how many saplings are in the calibration slot
 * @param paid whether a sapling has been spent on the measurement being built
 */
public record GuiState(
        Map<Item, Long> ratesPerHour,
        List<Item> overridden,
        int observedSeconds,
        int warmUpSeconds,
        boolean ready,
        long backlog,
        long lastCredit,
        boolean terminal,
        int saplings,
        boolean paid) {

    public static final GuiState EMPTY = new GuiState(Map.of(), List.of(), 0, 300, false, 0, 0, false, 0, false);

    public static final Codec<GuiState> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Ledger.COUNTS_CODEC.fieldOf("rates").forGetter(GuiState::ratesPerHour),
                    BuiltInRegistries.ITEM
                            .byNameCodec()
                            .listOf()
                            .fieldOf("overridden")
                            .forGetter(GuiState::overridden),
                    Codec.INT.fieldOf("observed_s").forGetter(GuiState::observedSeconds),
                    Codec.INT.fieldOf("warm_up_s").forGetter(GuiState::warmUpSeconds),
                    Codec.BOOL.fieldOf("ready").forGetter(GuiState::ready),
                    ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("backlog").forGetter(GuiState::backlog),
                    ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("last_credit").forGetter(GuiState::lastCredit),
                    Codec.BOOL.fieldOf("terminal").forGetter(GuiState::terminal),
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("saplings").forGetter(GuiState::saplings),
                    Codec.BOOL.fieldOf("paid").forGetter(GuiState::paid))
            .apply(i, GuiState::new));

    public GuiState {
        ratesPerHour = Map.copyOf(ratesPerHour);
        overridden = List.copyOf(overridden);
    }

    /** Whether the hopper is watching its farm, which the screen shows as a filling bar. */
    public boolean isCalibrating() {
        return paid && !ready && observedSeconds < warmUpSeconds;
    }

    /** Whether the hopper is waiting for the sapling a calibration run costs. */
    public boolean needsSapling() {
        return !paid && !ready;
    }

    /** How far calibration has come, from 0 to 1. */
    public float progress() {
        if (ready) {
            return 1.0F;
        }
        return warmUpSeconds <= 0 ? 0.0F : Math.clamp((float) observedSeconds / warmUpSeconds, 0.0F, 1.0F);
    }
}
