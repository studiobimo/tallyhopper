package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RateTest {

    @Test
    void perHourIsAnExactFraction() {
        assertThat(Rate.perHour(1000)).isEqualTo(new Rate(1000, 3_600_000));
        assertThat(Rate.perHour(1000).itemsPerHour()).isEqualTo(1000.0);
        assertThat(new Rate(1, 60_000).itemsPerHour()).isEqualTo(60.0);
    }

    @Test
    void rejectsNegativeItemsAndNonPositivePeriods() {
        assertThatThrownBy(() -> new Rate(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rate(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
