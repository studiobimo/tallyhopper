package dev.bimo.tallyhopper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.offline.EnergyFactors;
import dev.bimo.tallyhopper.platform.Services;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/tallyhopper.json}: the per-machine numbers behind the energy estimate. They belong to
 * the computer and its power grid rather than to the world, which is why they aren't gamerules.
 *
 * <p>A missing file is written with the defaults. An unreadable one is left alone and the defaults are
 * used, so a typo never costs a player their settings.
 */
public final class TallyHopperConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Contents contents = new Contents();

    private TallyHopperConfig() {}

    public static EnergyFactors energyFactors() {
        Contents current = contents;
        try {
            return new EnergyFactors(
                    current.watts, current.gridKilogramsCo2PerKilowattHour, current.litersWaterPerKilowattHour);
        } catch (IllegalArgumentException e) {
            TallyHopper.LOG.warn("Ignoring unusable energy factors in {}.json", TallyHopper.MOD_ID, e);
            return EnergyFactors.DEFAULTS;
        }
    }

    /** Whether the rejoin summary mentions the energy saved. */
    public static boolean showEnergyEstimate() {
        return contents.showEnergyEstimate;
    }

    /** Reads the config, writing it first if it isn't there. Called once from each loader's entry point. */
    public static void load() {
        Path path = Services.PLATFORM.configDir().resolve(TallyHopper.MOD_ID + ".json");
        try {
            if (Files.exists(path)) {
                Contents read = GSON.fromJson(Files.readString(path), Contents.class);
                if (read != null) {
                    contents = read;
                }
                return;
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(new Contents()));
        } catch (IOException | JsonSyntaxException e) {
            TallyHopper.LOG.warn("Using default settings; {} could not be read", path, e);
        }
    }

    /** The file's shape. Gson fills the fields it finds and leaves the defaults for the rest. */
    private static final class Contents {

        @SerializedName("_comment")
        @SuppressWarnings("UnusedVariable") // Gson writes it into the file for the player to read.
        String comment = "Used for the energy estimate in the rejoin summary."
                + " See docs/methodology.md for the sources of these numbers.";

        @SerializedName("pc_watts")
        double watts = EnergyFactors.DEFAULTS.watts();

        @SerializedName("grid_kg_co2_per_kwh")
        double gridKilogramsCo2PerKilowattHour = EnergyFactors.DEFAULTS.gridKilogramsCo2PerKilowattHour();

        @SerializedName("water_liters_per_kwh")
        double litersWaterPerKilowattHour = EnergyFactors.DEFAULTS.litersWaterPerKilowattHour();

        @SerializedName("show_energy_estimate")
        boolean showEnergyEstimate = true;
    }
}
