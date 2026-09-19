package dev.bimo.tallyhopper.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HopperChainTest {

    /** Hopper {@code i} pushes into the hopper in {@code links.get(i)}. */
    private final Map<Integer, Integer> links = new HashMap<>();

    private HopperChain.Result<Integer> walkFrom(int start) {
        return HopperChain.findEnd(start, links::get);
    }

    @Test
    void singleHopperIsItsOwnEnd() {
        assertThat(walkFrom(0)).isEqualTo(new HopperChain.Result.End<>(0, 1));
    }

    @Test
    void findsTheEndOfATenHopperChain() {
        for (int i = 0; i < 9; i++) {
            links.put(i, i + 1);
        }

        assertThat(walkFrom(0)).isEqualTo(new HopperChain.Result.End<>(9, 10));
        assertThat(walkFrom(6)).isEqualTo(new HopperChain.Result.End<>(9, 4));
    }

    @Test
    void stopsOnALoop() {
        links.put(0, 1);
        links.put(1, 2);
        links.put(2, 0);

        assertThat(walkFrom(0)).isInstanceOf(HopperChain.Result.Loop.class);
    }

    @Test
    void stopsOnALoopFurtherDownTheChain() {
        links.put(0, 1);
        links.put(1, 2);
        links.put(2, 3);
        links.put(3, 2);

        assertThat(walkFrom(0)).isInstanceOf(HopperChain.Result.Loop.class);
    }

    @Test
    void refusesChainsOverTheLimit() {
        for (int i = 0; i < 10; i++) {
            links.put(i, i + 1);
        }

        assertThat(HopperChain.findEnd(0, links::get, 11)).isEqualTo(new HopperChain.Result.End<>(10, 11));
        assertThat(HopperChain.findEnd(0, links::get, 10)).isInstanceOf(HopperChain.Result.TooLong.class);
    }
}
