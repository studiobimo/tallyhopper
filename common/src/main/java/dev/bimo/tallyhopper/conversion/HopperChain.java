package dev.bimo.tallyhopper.conversion;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class HopperChain {

    public static final int MAX_LENGTH = 256;

    private HopperChain() {}

    public sealed interface Result<P> {

        /**
         * The chain ends here.
         *
         * @param pos the last hopper in the chain
         * @param length how many hoppers the chain has, counting the start
         */
        record End<P>(P pos, int length) implements Result<P> {}

        record Loop<P>() implements Result<P> {}

        record TooLong<P>() implements Result<P> {}
    }

    public static <P> Result<P> findEnd(P start, Function<P, @Nullable P> next) {
        return findEnd(start, next, MAX_LENGTH);
    }

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
