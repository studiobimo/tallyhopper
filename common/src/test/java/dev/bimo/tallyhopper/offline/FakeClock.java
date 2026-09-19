package dev.bimo.tallyhopper.offline;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/** A clock that only moves when a test tells it to. */
final class FakeClock implements InstantSource {

    static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    private Instant now = EPOCH;

    @Override
    public Instant instant() {
        return now;
    }

    FakeClock advance(Duration by) {
        now = now.plus(by);
        return this;
    }
}
