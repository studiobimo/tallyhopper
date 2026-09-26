# ADR-0004: Terminal vs line delivery modes

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Credited items must reach storage without breaking redstone builds. Bulk-filling a chain of hoppers
would jam item-sorter filter hoppers. In a sorter, the last hopper of the line is the **overflow**
end, so measuring there would give the wrong rate.

## Decision

The mode is detected automatically from what the Tally Hopper faces:

- **Terminal mode** (faces a chest, barrel or modded storage): bulk-insert into it using its normal
  insertion rules, then send the remainder to the backlog.
- **Line mode** (faces another hopper): all credit goes to the backlog and flows through the
  player's line at vanilla speed, so it is safe for sorters.

One line earns once. A Tally Hopper that feeds another one, directly or down a line of vanilla
hoppers, credits nothing: both measured the same items, so both crediting would hand back one farm's
output once per Tally Hopper in the line. The hopper nearest the storage is the one that earns,
because it sees everything the line actually delivers. Converting a hopper upstream of an existing
Tally Hopper is refused before the sapling is spent, and a hopper that ends up upstream later says
so on its screen instead of showing a rate it will never be paid.

The backlog is exposed through the hopper's five slots, so pull consumers (hopper minecarts, and so on)
drain it naturally. Nothing is ever extracted from or replaced in existing inventories.

## Consequences

- Placement guidance matters, so the GUI shows the mode, an estimated drain time and a chain analysis.
- An opt-in "chain fill" was considered and rejected for v1.
- The chain is walked at credit time rather than tracked, so hoppers built, broken or re-aimed while
  the world is closed need no bookkeeping. The walk is bounded by `HopperChain.MAX_LENGTH` and runs
  once per hopper per session.
