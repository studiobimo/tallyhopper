package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EnergyEstimateTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void eightHoursAtTheDefaultFactors() {
        EnergyEstimate estimate = EnergyEstimate.of(Duration.ofHours(8), EnergyFactors.DEFAULTS);

        // 150 W for 8 h is 1.2 kWh.
        assertThat(estimate.kilowattHours()).isCloseTo(1.2, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(estimate.kilogramsCo2()).isCloseTo(1.2 * 0.394, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(estimate.litersWater()).isCloseTo(1.2 * 7.6, org.assertj.core.data.Offset.offset(TOLERANCE));
        // 60 kg over 10 years is about 16.4 g a day.
        assertThat(estimate.treeDays()).isCloseTo(28.79, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void scalesWithTimeAndFactors() {
        EnergyEstimate hour = EnergyEstimate.of(Duration.ofHours(1), new EnergyFactors(500, 0.2, 5));

        assertThat(hour.kilowattHours()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(hour.kilogramsCo2()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(hour.litersWater()).isCloseTo(2.5, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    void zeroTimeSavesNothing() {
        EnergyEstimate none = EnergyEstimate.of(Duration.ZERO, EnergyFactors.DEFAULTS);

        assertThat(none.kilowattHours()).isZero();
        assertThat(none.treeDays()).isZero();
    }

    @Test
    void rejectsNegativeTimeAndFactors() {
        assertThatThrownBy(() -> EnergyEstimate.of(Duration.ofSeconds(-1), EnergyFactors.DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnergyFactors(-1, 0.4, 7.6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnergyFactors(150, Double.NaN, 7.6)).isInstanceOf(IllegalArgumentException.class);
    }
}
