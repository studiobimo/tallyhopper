package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class SaturatingMathTest {

    @Test
    void addClampsInsteadOfWrapping() {
        assertThat(SaturatingMath.add(2, 3)).isEqualTo(5);
        assertThat(SaturatingMath.add(Long.MAX_VALUE, 1)).isEqualTo(Long.MAX_VALUE);
        assertThat(SaturatingMath.add(Long.MAX_VALUE, Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void clampCapsLargeValues() {
        assertThat(SaturatingMath.clamp(BigInteger.TEN)).isEqualTo(10);
        assertThat(SaturatingMath.clamp(BigInteger.TWO.pow(100))).isEqualTo(Long.MAX_VALUE);
    }
}
