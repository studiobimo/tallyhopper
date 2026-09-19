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

The backlog is exposed through the hopper's five slots, so pull consumers (hopper minecarts, and so on)
drain it naturally. Nothing is ever extracted from or replaced in existing inventories.

## Consequences

- Placement guidance matters, so the GUI shows the mode, an estimated drain time and a chain analysis.
- An opt-in "chain fill" was considered and rejected for v1.
