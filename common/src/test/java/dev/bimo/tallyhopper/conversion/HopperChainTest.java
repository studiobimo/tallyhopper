package dev.bimo.tallyhopper.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    void findsAMarkedHopperAnywhereInTheChain() {
        for (int i = 0; i < 9; i++) {
            links.put(i, i + 1);
        }
        Set<Integer> marked = Set.of(5);

        assertThat(HopperChain.anyInChain(0, links::get, marked::contains)).isTrue();
        assertThat(HopperChain.anyInChain(5, links::get, marked::contains)).isTrue();
        assertThat(HopperChain.anyInChain(6, links::get, marked::contains)).isFalse();
        assertThat(HopperChain.anyInChain(0, links::get, Set.of(99)::contains)).isFalse();
    }

    @Test
    void looksAtEveryHopperOfALoop() {
        links.put(0, 1);
        links.put(1, 2);
        links.put(2, 0);

        assertThat(HopperChain.anyInChain(0, links::get, Set.of(2)::contains)).isTrue();
        assertThat(HopperChain.anyInChain(0, links::get, Set.of(9)::contains)).isFalse();
    }

    /** A chain too long to follow is answered from the part that was walked, and never hangs. */
    @Test
    void answersALongChainFromWhatItWalked() {
        for (int i = 0; i < 20; i++) {
            links.put(i, i + 1);
        }

        assertThat(HopperChain.anyInChain(0, links::get, Set.of(3)::contains, 5))
                .isTrue();
        assertThat(HopperChain.anyInChain(0, links::get, Set.of(15)::contains, 5))
                .isFalse();
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
