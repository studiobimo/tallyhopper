package dev.bimo.tallyhopper.conversion;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Follows a line of hoppers, each pushing into the next, to the one at the end.
 *
 * <p>Kept free of Minecraft types so it can be unit-tested: the caller supplies {@code next}, which
 * returns the hopper a position pushes into, or {@code null} when it pushes into anything else.
 */
public final class HopperChain {

    /** Longer chains are refused rather than walked, so a click can never stall the server. */
    public static final int MAX_LENGTH = 256;

    private HopperChain() {}

    /** Where the walk stopped. */
    public sealed interface Result<P> {

        /**
         * The chain ends here.
         *
         * @param pos the last hopper in the chain
         * @param length how many hoppers the chain has, counting the start
         */
        record End<P>(P pos, int length) implements Result<P> {}

        /** The chain feeds back into itself, so it has no end. */
        record Loop<P>() implements Result<P> {}

        /** The chain is longer than {@link #MAX_LENGTH}. */
        record TooLong<P>() implements Result<P> {}
    }

    public static <P> Result<P> findEnd(P start, Function<P, @Nullable P> next) {
        return findEnd(start, next, MAX_LENGTH);
    }

    /**
     * Whether any hopper from {@code start} onwards, {@code start} included, matches {@code test}.
     *
     * <p>A chain that loops or runs past {@link #MAX_LENGTH} is answered from what was walked before
     * it stopped, which is every hopper of a loop and the first {@link #MAX_LENGTH} of a chain too
     * long to follow.
     */
    public static <P> boolean anyInChain(P start, Function<P, @Nullable P> next, Predicate<P> test) {
        return anyInChain(start, next, test, MAX_LENGTH);
    }

    static <P> boolean anyInChain(P start, Function<P, @Nullable P> next, Predicate<P> test, int maxLength) {
        Set<P> visited = new HashSet<>();
        P current = start;
        while (visited.add(current) && visited.size() <= maxLength) {
            if (test.test(current)) {
                return true;
            }
            P following = next.apply(current);
            if (following == null) {
                return false;
            }
            current = following;
        }
        return false;
    }

    static <P> Result<P> findEnd(P start, Function<P, @Nullable P> next, int maxLength) {
        Set<P> visited = new HashSet<>();
        visited.add(start);
        P current = start;
        while (true) {
            P following = next.apply(current);
            if (following == null) {
                return new Result.End<>(current, visited.size());
            }
            if (!visited.add(following)) {
                return new Result.Loop<>();
            }
            if (visited.size() > maxLength) {
                return new Result.TooLong<>();
            }
            current = following;
        }
    }
}
