package dev.bimo.tallyhopper.offline;

import java.time.Duration;

/**
 * A rough estimate of what was saved by not leaving a computer running, for the rejoin summary.
 *
 * <p>Every figure is an average, so the summary always prefixes them with "≈" and speaks of
 * "tree-days of CO₂ uptake", never "trees saved". The factors are configurable; see
 * {@code docs/methodology.md} for the sources.
 *
 * @param kilowattHours electricity not used
 * @param kilogramsCo2 emissions avoided at the grid's average intensity
 * @param litersWater water not consumed generating that electricity
 * @param treeDays days one urban tree would need to take up that much CO₂
 */
public record EnergyEstimate(double kilowattHours, double kilogramsCo2, double litersWater, double treeDays) {

    /**
     * The EPA figure for an urban tree seedling: 60 kg of CO₂ over 10 years.
     */
    private static final double TREE_KG_PER_DAY = 60.0 / (10 * 365.25);

    public static EnergyEstimate of(Duration offline, EnergyFactors factors) {
        if (offline.isNegative()) {
            throw new IllegalArgumentException("offline must not be negative: " + offline);
        }
        double hours = offline.toMillis() / 3_600_000.0;
        double kilowattHours = factors.watts() * hours / 1000.0;
        double kilogramsCo2 = kilowattHours * factors.gridKilogramsCo2PerKilowattHour();
        return new EnergyEstimate(
                kilowattHours,
                kilogramsCo2,
                kilowattHours * factors.litersWaterPerKilowattHour(),
                kilogramsCo2 / TREE_KG_PER_DAY);
    }
}
