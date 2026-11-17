package dev.bimo.tallyhopper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TallyHopperTest {

    @Test
    void modIdIsAValidResourceNamespace() {
        assertThat(TallyHopper.MOD_ID).matches("[a-z0-9_.-]+");
    }
}
