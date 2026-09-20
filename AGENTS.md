# Agent guide: Tally Hopper

Instructions for AI coding agents (Codex, Claude Code, and others) working in this repo.

## Project

A Minecraft Java mod (Fabric + NeoForge, Minecraft 26.3, Java 25) built from
MultiLoader-Template. Most code lives in `common/` and compiles against vanilla only.
Loader-specific code lives in `fabric/` and `neoforge/` behind `Services` interfaces.

- Roadmap and exit criteria: `docs/ROADMAP.md`
- Design decisions: `docs/adr/`

## Non-negotiables

- **Commits:** [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).
  Scopes: `common`, `fabric`, `neoforge`, `build`, `ci`, `docs`, `deps`, `devtools`.
- **Branches:** [Conventional Branch](https://conventionalbranch.org/), e.g. `feat/offline-credit`.
  Agents may use `claude/…` or `codex/…`.
- **PR size:** at most 20 changed files. Split bigger work with `gh stack`
  (`gh stack init`, `gh stack add`, `gh stack submit`).
- **Versioning:** SemVer, managed by release-please. Never edit `version` in `gradle.properties` by hand.
- **Pinning:** GitHub Actions pinned to full SHAs; dependencies locked (`gradle.lockfile`)
  and checksum-verified (`gradle/verification-metadata.xml`). After changing a version, run
  `make -C .devtools lock`.
- **Behavior:** the mod is strictly additive. It must never delete, replace or extract
  items a player already has.

A PreToolUse hook (`.devtools/scripts/agent-guard.sh`) blocks `gh pr create`, `gh stack submit`
and `git push` when the PR-size rule is violated, and blocks non-conventional branch names.

## Commands

```sh
make -C .devtools setup   # once: pinned tools + git hooks
make -C .devtools check   # everything CI runs (lint + build + tests)
make -C .devtools fmt     # format Java
make -C .devtools run-fabric-server
make -C .devtools stray   # JVMs a dev run or GameTest left behind (kill-stray stops them)
```

## Code conventions

- Formatting: palantir-java-format via Spotless. Don't hand-format.
- Nullness: JSpecify + NullAway. Our packages are non-null by default; mark nullable with
  `@org.jspecify.annotations.Nullable`.
- Keep game-independent logic (rate math, credit, backlog) Minecraft-free, with JUnit tests
  in `common/src/test`. Coverage gate: 90% lines on `dev.bimo.tallyhopper.offline`.
- Verify Minecraft 26.x API names against the decompiled sources. 26.1+ is unobfuscated and
  uses Mojang names, so APIs differ from older tutorials.
