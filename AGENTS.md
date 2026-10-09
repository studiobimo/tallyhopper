# Agent guide: Tally Hopper

Instructions for AI coding agents (Codex, Claude Code, and others) working in this repo.

## Project

A Minecraft Java mod (Fabric + NeoForge, Minecraft 26.3, Java 25) built from
MultiLoader-Template. Most code lives in `common/` and compiles against vanilla only.
Loader-specific code lives in `fabric/` and `neoforge/` behind `Services` interfaces.

- Roadmap and exit criteria: `docs/ROADMAP.md`
- Design decisions: `docs/adr/`
- Workflow and conventions in full: `CONTRIBUTING.md`
- Commit scopes: `common`, `fabric`, `neoforge`, `build`, `ci`, `docs`, `deps`, `devtools`.
  `.commitlintrc.yaml` enforces this list, so add a scope in both places.

<!-- >>> template:rules -->
## Non-negotiables

These hold in every studiobimo repo. Git hooks, CI and an agent hook all enforce them, so a
violation is caught before it is reviewed.

- **Commits:** [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/),
  `<type>(<scope>): <summary>`. PRs are squash-merged, so the **PR title** must be one too.
  commitlint checks both against its conventional config: a lowercase summary with no full stop,
  at most 100 characters in the header and in each body line, and a scope from the project's list.
- **Branches:** [Conventional Branch](https://conventionalbranch.org/), `<type>/<description>`
  in lowercase with single hyphens, e.g. `feat/short-description`. Agents may use `claude/…` or
  `codex/…`.
- **PR size:** at most 20 changed files. Split bigger work with `gh stack`
  (`gh stack init`, `gh stack add`, `gh stack submit`).
- **Versioning:** SemVer, managed by release-please. Never edit a version, a
  `.release-please-manifest.json` or a `CHANGELOG.md` by hand.
- **Pinning:** third-party GitHub Actions are pinned to full commit SHAs with the version in a
  comment; studiobimo's own reusable workflows are called at `@v1`. Every tool is pinned in
  `mise.toml` and locked in `mise.lock`. Dependabot does not read `mise.toml`: a tool is bumped by
  hand, then `make -C .devtools lock`, and `mise.toml`, `mise.lock` and `.mise/locks/` are
  committed together.
- **Workflows:** `permissions: {}` at the top, the minimum per job, `persist-credentials: false`
  on every checkout, secrets passed explicitly and never with `secrets: inherit`.
- **Say what you tested.** State what you ran and what it showed. If something could not be
  tested, say so plainly rather than implying it was.

A PreToolUse hook (`.devtools/scripts/agent-guard.sh`) blocks `gh pr create`, `gh stack submit`
and `git push` when the PR-size rule is violated, and blocks non-conventional branch names.

## Where shared things live

Some files here are not this repo's to edit. Changing them locally only creates drift, which a
weekly workflow reports as an issue.

| To change | Edit it in | It reaches this repo by |
| --- | --- | --- |
| CI behaviour (lint, PR checks, release) | `studiobimo/.github`, `.github/workflows/` | the `@v1` tag moving |
| Branch and PR-size rules | `studiobimo/.github`, `.devtools/` | the `@v1` tag moving; lefthook refetches it daily |
| Shared hooks and tool versions | `studiobimo/project-template` | `make -C .devtools sync` |
| Files and blocks listed in the template's `.template/manifest` | `studiobimo/project-template` | `make -C .devtools sync` |

A managed block sits between `>>> template:<name>` and `<<< template:<name>` marker lines, like
this section. Edit outside the markers freely; inside them, change the template instead. If a
difference is deliberate, list the path in `.template-ignore` with a comment saying why.
<!-- <<< template:rules -->

## Mod rules

- **Version:** release-please owns `version` in `gradle.properties`. Never edit it by hand.
- **Dependencies:** locked (`gradle.lockfile`) and checksum-verified
  (`gradle/verification-metadata.xml`). After changing a version, run `make -C .devtools lock`.
- **Behavior:** the mod is strictly additive. It must never delete, replace or extract
  items a player already has.

## Commands

```sh
make -C .devtools setup   # once: pinned tools + git hooks
make -C .devtools check   # everything CI runs (lint + build + tests)
make -C .devtools fmt     # format Java
make -C .devtools run-fabric-server
make -C .devtools stray   # JVMs a dev run or GameTest left behind (kill-stray stops them)
make -C .devtools drift   # where this repo differs from studiobimo/project-template
make -C .devtools sync    # pull the template's managed files
```

## Code conventions

- Formatting: palantir-java-format via Spotless. Don't hand-format.
- Nullness: JSpecify + NullAway. Our packages are non-null by default; mark nullable with
  `@org.jspecify.annotations.Nullable`.
- Keep game-independent logic (rate math, credit, backlog) Minecraft-free, with JUnit tests
  in `common/src/test`. Coverage gate: 90% lines on `dev.bimo.tallyhopper.offline`.
- Verify Minecraft 26.x API names against the decompiled sources. 26.1+ is unobfuscated and
  uses Mojang names, so APIs differ from older tutorials.
