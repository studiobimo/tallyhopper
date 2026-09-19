package dev.bimo.tallyhopper.offline;

/**
 * The per-machine and per-grid averages the energy estimate uses. They differ from place to place, so
 * players can change them in {@code config/tallyhopper.json}.
 *
 * @param watts what the computer would have drawn while idling a world
 * @param gridKilogramsCo2PerKilowattHour the grid's average emissions, EPA's avoided-emissions factor
 * @param litersWaterPerKilowattHour water consumed generating electricity, an EIA/NREL average
 */
public record EnergyFactors(double watts, double gridKilogramsCo2PerKilowattHour, double litersWaterPerKilowattHour) {

    public static final EnergyFactors DEFAULTS = new EnergyFactors(150, 0.394, 7.6);

    public EnergyFactors {
        require(watts, "watts");
        require(gridKilogramsCo2PerKilowattHour, "gridKilogramsCo2PerKilowattHour");
        require(litersWaterPerKilowattHour, "litersWaterPerKilowattHour");
    }

    private static void require(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be zero or more: " + value);
        }
    }
}
