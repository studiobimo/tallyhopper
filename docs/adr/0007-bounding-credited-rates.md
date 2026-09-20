# ADR-0007: Bounding what a hopper may credit

- **Status:** Accepted
- **Date:** 2026-09-19

## Context

A Tally Hopper credits `rate × time away`, where the rate is measured over a rolling window of the
last hour of running time ([ADR-0003](0003-measured-rate-model.md)). Two properties of that model let
a player mint items from a farm that produces nothing:

- **A short window is a weak claim.** A hopper becomes ready after five minutes of watching, but its
  rate is then extrapolated over up to 24 hours. Hand-feeding items into the hopper for those five
  minutes reads as an enormous rate.
- **Nothing bounded the rate.** A measured rate could be any size at all, and an override could be
  set to match it: `/tallyhopper rate set` compares the requested rate against the measurement *at
  that moment* and then stores the number permanently. A burst could therefore be frozen into an
  override that never decays, outliving the window that justified it and surviving a recalibration.

The rolling window undoes an inflated measurement for a player who keeps playing, but not for one who
bursts and immediately quits, and not at all once the number has been copied into an override.

## Decision

Two bounds, both in `RateBounds`, applied wherever a rate is shown or paid out.

**Weighing.** A measured rate is scaled by `observed / window`: the share of the hour-long window the
hopper has actually watched. Five minutes of watching credits a twelfth of the measured rate; an hour
credits all of it. Overrides are not weighed, because a player set them deliberately.

**A ceiling.** The total of all rates for one hopper is held to `tallyhopper:max_items_per_hour`,
default 9,000 — one item every eight ticks, which is what a hopper can physically move. When the
total is over the ceiling, every rate is scaled down together so the mix between items is kept.

Everything a Tally Hopper credits passed through its five slots at vanilla speed, so no honest farm
can sit above that ceiling for long. Only a window too short to see the truth can.

Both bounds are exact: rates stay fractions and are reduced rather than rounded, so a farm that makes
one diamond an hour still earns during its first hour instead of being rounded away to nothing.

The ceiling also becomes the limit a player who is not an operator may override up to, so the burst →
read the number → freeze it as an override path is closed at both ends.

## Alternatives considered

**Re-checking overrides against the measurement at credit time.** Instead of bounding the rate, credit
`min(override, measured)` every session, so an override could never outlive the measurement that
justified it. Rejected: it defeats the main legitimate use of an override, which is to keep crediting
a farm the hopper cannot currently measure — one that is jammed, switched off for building work, or
freshly rebuilt. That player deliberately set a number the measurement does not support, which is the
whole point. The ceiling bounds the damage without taking the feature away. **If the override path
turns out to be abused in practice, this is the fix to reach for**, ideally as a gamerule so a server
can choose.

**Freezing the rate behind the screen's padlock.** Considered and rejected in
[ADR-0006](0006-screen-from-vanilla-parts.md) for the same family of reasons: it would have made an
inflated measurement permanent.

## Consequences

- A hopper that has just finished calibrating credits a twelfth of what it measured, and reaches its
  full rate after an hour of running. The screen says so: "Credits at 8% until an hour is watched".
- `/tallyhopper info`, the screen and the credit itself all read the same bounded numbers, so none of
  them can promise more than the hopper pays.
- A farm that genuinely saturates a hopper is unaffected: it was already at the ceiling.
- A server that wants the old behaviour can raise `tallyhopper:max_items_per_hour`. Lowering it below
  a farm's real output would hold that farm back, which is why the default is a physical limit rather
  than a balance number.
