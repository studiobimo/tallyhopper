# ADR-0003: Measured-rate credit model

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Offline output could be (a) configured by hand, (b) simulated by replaying mob spawns
(as [Anabiosis](https://modrinth.com/mod/anabiosis) does), or (c) extrapolated from measured throughput.

## Decision

Use **measured-rate extrapolation**, with an optional **per-hopper manual override**.

- Each Tally Hopper counts items per type in rolling wall-clock buckets, but only while it is
  ticking. Pauses are excluded.
- Offline credit is `rate × offline time`. The offline time comes from a heartbeat saved every 30 s,
  clamped (default cap 24 h) and credited once per session.
- A hopper is eligible only if it was ticking when the world closed.
- Only stacks with default components are extrapolated. Unique items (enchanted drops, named items)
  are never duplicated.

## Consequences

- Works for any farm type, because it only observes items, and it is cheap to compute.
- The rate reflects real throughput, including hopper speed limits and a farm's warm-up. The
  estimate is honest but conservative.
- Players can exploit it by feeding items into a hopper's intake. For a single-player
  quality-of-life mod this is accepted; the gamerules let servers disable or cap it.
