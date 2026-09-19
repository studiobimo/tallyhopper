package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BacklogTest {

    /** A credit map with a fixed iteration order, so tie-breaks are predictable. */
    private static Map<String, Long> credit(Object... itemsAndCounts) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            map.put((String) itemsAndCounts[i], ((Number) itemsAndCounts[i + 1]).longValue());
        }
        return map;
    }

    @Test
    void storesCreditWithinTheCap() {
        Backlog<String> backlog = new Backlog<>(Backlog.DEFAULT_CAP);

        long refused = backlog.addAll(credit("iron", 8000, "poppy", 12));
        backlog.addAll(credit("iron", 500));

        assertThat(refused).isZero();
        assertThat(backlog.get("iron")).isEqualTo(8500);
        assertThat(backlog.total()).isEqualTo(8512);
        assertThat(backlog.contents()).containsExactly(Map.entry("iron", 8500L), Map.entry("poppy", 12L));
    }

    @Test
    void capHitSharesTheRoomInProportion() {
        Backlog<String> backlog = new Backlog<>(1000);

        long refused = backlog.addAll(credit("iron", 3000, "poppy", 1000));

        assertThat(refused).isEqualTo(3000);
        assertThat(backlog.get("iron")).isEqualTo(750);
        assertThat(backlog.get("poppy")).isEqualTo(250);
        assertThat(backlog.total()).isEqualTo(backlog.cap());
    }

    @Test
    void roundingLeftoversAreHandedOutOneEach() {
        Backlog<String> backlog = new Backlog<>(10);

        long refused = backlog.addAll(credit("a", 7, "b", 7, "c", 7));

        assertThat(refused).isEqualTo(11);
        assertThat(backlog.contents()).containsExactly(Map.entry("a", 4L), Map.entry("b", 3L), Map.entry("c", 3L));
    }

    @Test
    void leftoversSkipItemsThatAlreadyGotEverything() {
        Backlog<String> backlog = new Backlog<>(3);

        long refused = backlog.addAll(credit("rare", 1, "iron", 9));

        assertThat(refused).isEqualTo(7);
        assertThat(backlog.contents()).containsExactly(Map.entry("rare", 1L), Map.entry("iron", 2L));
    }

    @Test
    void loweringTheCapKeepsExistingItemsAndRefusesNewOnes() {
        Backlog<String> backlog = new Backlog<>(100);
        backlog.addAll(credit("iron", 80));

        backlog.setCap(50);
        long refused = backlog.addAll(credit("iron", 10));

        assertThat(refused).isEqualTo(10);
        assertThat(backlog.get("iron")).isEqualTo(80);
    }

    @Test
    void restoreKeepsContentsOverTheCap() {
        Backlog<String> backlog = new Backlog<>(credit("iron", 70, "gold", 0), 10);

        assertThat(backlog.total()).isEqualTo(70);
        assertThat(backlog.contents()).containsOnlyKeys("iron");
    }

    @Test
    void takeRemovesUpToWhatIsHeld() {
        Backlog<String> backlog = new Backlog<>(credit("iron", 100), Backlog.DEFAULT_CAP);

        assertThat(backlog.take("iron", 64)).isEqualTo(64);
        assertThat(backlog.get("iron")).isEqualTo(36);
        assertThat(backlog.take("iron", 64)).isEqualTo(36);
        assertThat(backlog.take("iron", 64)).isZero();
        assertThat(backlog.take("gold", 64)).isZero();
        assertThat(backlog.isEmpty()).isTrue();
        assertThat(backlog.contents()).isEmpty();
    }

    @Test
    void hugeCountsSaturateInsteadOfOverflowing() {
        Backlog<String> backlog = new Backlog<>(Long.MAX_VALUE);

        long refused = backlog.addAll(credit("iron", Long.MAX_VALUE - 1, "gold", Long.MAX_VALUE));

        assertThat(backlog.total()).isEqualTo(Long.MAX_VALUE);
        assertThat(refused).isEqualTo(Long.MAX_VALUE - 1);
        assertThat(backlog.get("iron") + backlog.get("gold")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsNegativeValues() {
        Backlog<String> backlog = new Backlog<>(10);

        assertThatThrownBy(() -> new Backlog<String>(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backlog.addAll(credit("iron", -1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backlog.take("iron", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Backlog<>(credit("iron", -1), 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void peekHandsOutTheOldestItemFirst() {
        Backlog<String> backlog = new Backlog<>(Backlog.DEFAULT_CAP);
        assertThat(backlog.peek()).isNull();

        backlog.addAll(credit("iron", 3, "poppy", 2));
        assertThat(backlog.peek()).isEqualTo("iron");

        backlog.take("iron", 3);
        assertThat(backlog.peek()).isEqualTo("poppy");
    }
}
