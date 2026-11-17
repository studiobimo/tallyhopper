package dev.bimo.tallyhopper.offline;

import java.math.BigInteger;

public final class SaturatingMath {

    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private SaturatingMath() {}

    public static long add(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    static long clamp(BigInteger value) {
        return value.compareTo(MAX) > 0 ? Long.MAX_VALUE : value.longValueExact();
    }
}
