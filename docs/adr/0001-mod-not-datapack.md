# ADR-0001: A mod, not a datapack

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Crediting offline output requires knowing how much **real-world** time passed while the world
was closed. A datapack was the preferred option, because it needs no installation.

## Decision

Build a **mod**.

- Vanilla commands cannot read the wall clock. In-game time (`/time query gametime`) stops while
  the world is closed.
- The only known datapack workaround ([TimeLib](https://github.com/BluesCrew/UnixLib/)) has three problems:
  - it needs internet access, because it decodes timestamps from Mojang-served player-head textures;
  - it combines that with a command-block `LastOutput` trick that only gives HH:MM:SS;
  - it force-loads chunks at the world border.
- Datapacks get no shutdown event, so they cannot record the exit time reliably.
- Datapacks cannot tell farm output apart from items a player inserts. They also cannot hold a
  large backlog without spawning item entities.

## Consequences

- Players must install a mod loader. On Fabric they also need Fabric API.
- The mod has full access to lifecycle events, real time (`java.time`), block entities and the
  loaders' item-transfer APIs.
