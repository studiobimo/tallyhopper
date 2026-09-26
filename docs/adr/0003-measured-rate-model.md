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
- Items a player threw in by hand are picked up but not counted. Vanilla marks an item entity with
  its thrower only for a deliberate throw, so mining, farming, mob drops, death drops and inventory
  overflow all still count.

## Consequences

- Works for any farm type, because it only observes items, and it is cheap to compute.
- The rate reflects real throughput, including hopper speed limits and a farm's warm-up. The
  estimate is honest but conservative.
- Throwing a stack straight into a hopper no longer inflates its rate, which was the exploit a
  player hits by accident: dropping something while building next to a farm.
- Feeding items in less directly is still possible — through a chest, a dropper or a vanilla hopper
  upstream, where the thrower is forgotten once the stack is inside a container. Vanilla also drops
  the thrower when two item entities merge. Closing those would mean auditing every container, which
  costs more than it is worth here; the gamerules let servers cap or disable credit instead.
