package dev.bimo.tallyhopper.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateBoundsTest {

    private static final Duration WINDOW = Duration.ofHours(1);

    @Nested
    @DisplayName("weighing a rate by how long it was watched")
    class Weighing {

        @Test
        void aFullWindowIsBelievedInFull() {
            Map<String, Rate> measured = Map.of("cobblestone", Rate.perHour(600));

            Map<String, Rate> weighed = RateBounds.weigh(measured, WINDOW, WINDOW);

            assertThat(weighed.get("cobblestone").itemsPerHour()).isEqualTo(600);
        }

        @Test
        void watchingLongerThanTheWindowChangesNothing() {
            Map<String, Rate> measured = Map.of("cobblestone", Rate.perHour(600));

            Map<String, Rate> weighed = RateBounds.weigh(measured, Duration.ofHours(5), WINDOW);

            assertThat(weighed.get("cobblestone").itemsPerHour()).isEqualTo(600);
        }

        @Test
        void fiveMinutesOfWatchingIsWorthATwelfth() {
            Map<String, Rate> measured = Map.of("cobblestone", Rate.perHour(120_000));

            Map<String, Rate> weighed = RateBounds.weigh(measured, Duration.ofMinutes(5), WINDOW);

            assertThat(weighed.get("cobblestone").itemsPerHour()).isEqualTo(10_000);
        }

        @Test
        void nothingWatchedEarnsNothing() {
            Map<String, Rate> measured = Map.of("cobblestone", Rate.perHour(600));

            assertThat(RateBounds.weigh(measured, Duration.ZERO, WINDOW)).isEmpty();
        }

        @Test
        void aSlowFarmKeepsASmallRateRatherThanBeingRoundedAway() {
            Map<String, Rate> measured = Map.of("diamond", Rate.perHour(1));

            Map<String, Rate> weighed = RateBounds.weigh(measured, Duration.ofMinutes(5), WINDOW);

            assertThat(weighed.get("diamond").itemsPerHour())
                    .isCloseTo(1.0 / 12, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(weighed.get("diamond").items()).isPositive();
        }

        @Test
        void aWindowOfNoLengthIsLeftAlone() {
            Map<String, Rate> measured = Map.of("cobblestone", Rate.perHour(600));

            Map<String, Rate> weighed = RateBounds.weigh(measured, Duration.ofMinutes(5), Duration.ZERO);

            assertThat(weighed.get("cobblestone").itemsPerHour()).isEqualTo(600);
        }
    }

    @Nested
    @DisplayName("capping the total a hopper may credit")
    class Capping {

        @Test
        void ratesUnderTheCeilingAreLeftAlone() {
            Map<String, Rate> rates = Map.of("cobblestone", Rate.perHour(600));

            Map<String, Rate> capped = RateBounds.capTotal(rates, RateBounds.HOPPER_ITEMS_PER_HOUR);

            assertThat(capped.get("cobblestone").itemsPerHour()).isEqualTo(600);
        }

        @Test
        void aBurstIsHeldToWhatAHopperCanMove() {
            Map<String, Rate> rates = Map.of("cobblestone", Rate.perHour(400_000));

            Map<String, Rate> capped = RateBounds.capTotal(rates, RateBounds.HOPPER_ITEMS_PER_HOUR);

            assertThat(capped.get("cobblestone").itemsPerHour())
                    .isCloseTo(9000, org.assertj.core.data.Offset.offset(1.0));
        }

        @Test
        void theCeilingIsOnTheTotalAndKeepsTheMixBetweenItems() {
            Map<String, Rate> rates = new LinkedHashMap<>();
            rates.put("cobblestone", Rate.perHour(9000));
            rates.put("dirt", Rate.perHour(3000));

            Map<String, Rate> capped = RateBounds.capTotal(rates, RateBounds.HOPPER_ITEMS_PER_HOUR);

            double cobblestone = capped.get("cobblestone").itemsPerHour();
            double dirt = capped.get("dirt").itemsPerHour();
            assertThat(cobblestone + dirt).isCloseTo(9000, org.assertj.core.data.Offset.offset(1.0));
            assertThat(cobblestone / dirt).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        void aCeilingOfZeroCreditsNothing() {
            Map<String, Rate> rates = Map.of("cobblestone", Rate.perHour(600));

            assertThat(RateBounds.capTotal(rates, 0)).isEmpty();
        }

        @Test
        void aNegativeCeilingIsRejected() {
            assertThatThrownBy(() -> RateBounds.capTotal(Map.of("cobblestone", Rate.perHour(1)), -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxPerHour");
        }

        @Test
        void aSaturatedRateIsStillHeldToTheCeiling() {
            Map<String, Rate> rates = Map.of("cobblestone", new Rate(Long.MAX_VALUE, 1));

            Map<String, Rate> capped = RateBounds.capTotal(rates, RateBounds.HOPPER_ITEMS_PER_HOUR);

            assertThat(capped.get("cobblestone").itemsPerHour())
                    .isLessThanOrEqualTo(RateBounds.HOPPER_ITEMS_PER_HOUR)
                    .isGreaterThan(0);
        }

        @Test
        void anEmptySetOfRatesTotalsNothing() {
            assertThat(RateBounds.totalPerHour(Map.of())).isEqualTo(java.math.BigInteger.ZERO);
        }
    }

    @Test
    @DisplayName("both bounds together: a five-minute burst is worth minutes, not days")
    void aBurstIsWorthWhatItWatched() {
        // 30,000 items hand-fed in five minutes reads as 360,000 an hour.
        Map<String, Rate> measured =
                Map.of("cobblestone", new Rate(30_000, Duration.ofMinutes(5).toMillis()));

        Map<String, Rate> weighed = RateBounds.weigh(measured, Duration.ofMinutes(5), WINDOW);
        Map<String, Rate> credited = RateBounds.capTotal(weighed, RateBounds.HOPPER_ITEMS_PER_HOUR);

        assertThat(measured.get("cobblestone").itemsPerHour())
                .isCloseTo(360_000, org.assertj.core.data.Offset.offset(1.0));
        assertThat(weighed.get("cobblestone").itemsPerHour())
                .isCloseTo(30_000, org.assertj.core.data.Offset.offset(1.0));
        assertThat(credited.get("cobblestone").itemsPerHour())
                .isCloseTo(RateBounds.HOPPER_ITEMS_PER_HOUR, org.assertj.core.data.Offset.offset(1.0));
    }
}
