# ADR-0002: Multiloader (Fabric + NeoForge)

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

Minecraft 26.1+ is unobfuscated and compiled against Mojang names, and both major loaders
support it. Supporting both loaders reaches the whole technical-player audience.

## Decision

Use [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) (CC0, 26.3 branch):

- `common/` compiles against vanilla only (via NeoForm) and holds almost all code;
- `fabric/` and `neoforge/` are thin entry points plus `Services` (ServiceLoader) implementations;
- there is no runtime abstraction library (such as Architectury), so there are zero extra dependencies for players.

## Consequences

- Loader-specific features (transfer APIs, networking, events) need a small interface in common
  and two implementations.
- Loader-specific code is not type-checked from common, so both loaders must be tested (GameTests on each).
