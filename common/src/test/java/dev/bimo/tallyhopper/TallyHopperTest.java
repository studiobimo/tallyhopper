package dev.bimo.tallyhopper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TallyHopperTest {

    @Test
    void modIdIsAValidResourceNamespace() {
        // Minecraft namespaces allow only [a-z0-9_.-]; anything else fails registry keys at runtime.
        assertThat(TallyHopper.MOD_ID).matches("[a-z0-9_.-]+");
    }
}
