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
- **Issues:** titled the same way, like the commit that will close them: `fix: …`, `feat: …`,
  `docs: …`. The issue forms start the title with a type, and the `issue-title` workflow labels
  and comments on any issue whose title fails commitlint until it is fixed.
- **Branches:** [Conventional Branch](https://conventionalbranch.org/), `<type>/<description>`
  in lowercase with single hyphens, e.g. `feat/short-description`. Agents may use `claude/…` or
  `codex/…`.
- **PR size:** at most 20 changed files. Split bigger work with `gh stack`
  (`gh stack init`, `gh stack add`, `gh stack submit`).
- **Versioning:** SemVer, set by the project's release tooling. Never edit a version, create a
  tag, or write a `CHANGELOG.md` or release manifest by hand.
- **Pinning:** third-party GitHub Actions are pinned to full commit SHAs with the version in a
  comment; studiobimo's own reusable workflows are called at `@v1`. Every tool is pinned in
  `mise.toml` and locked in `mise.lock`. Dependabot does not read `mise.toml`: a tool is bumped by
  hand, then `make -C .devtools lock`, and `mise.toml`, `mise.lock` and `.mise/locks/` are
  committed together.
- **Workflows:** `permissions: {}` at the top, the minimum per job, `persist-credentials: false`
  on every checkout, secrets passed explicitly and never with `secrets: inherit`.
- **No comments** in code files. Code that seems to need one gets a well-named helper or clearer
  names instead. A suppression (`eslint-disable`, `@ts-ignore`, `# noqa`) is a finding too: fix
  what the linter flags. `.uncomment.toml` sets what `uncomment` checks, in the pre-commit hook.
- **One source of truth.** Domain logic, validation rules, schemas and configuration values live
  in exactly one place, in application code, tests, build scripts and workflows alike. Replace
  duplication with shared functions or modules: fewer lines through consolidation, never through
  clever one-liners. `make -C .devtools check` runs jscpd (`.jscpd.json`), and knip where there
  is a `package.json`.
- **Repo health:** the `repo-health` workflow runs these and more (lizard: functions under 80
  lines) over every tracked file. A finding fails it unless `.repo-health/baseline.json` lists it
  with an open issue, and each pull request resolves at least one listed finding while any are
  left. Changing an analyser's config needs the `repo-health-config-change` label, which
  `make -C .devtools github` creates.
- **Constants by domain:** grouped in objects, `DOMAIN.group.name`, not `DOMAIN_GROUP_NAME`.
- **Say what you tested.** State what you ran and what it showed. If something could not be
  tested, say so plainly rather than implying it was.

A PreToolUse hook (`.devtools/scripts/agent-guard.sh`) blocks `gh pr create`, `gh stack submit`
and `git push` when the PR-size rule is violated, and blocks non-conventional branch names from
`git switch -c`, `git checkout -b`, `git worktree add -b`, `git branch` and `gh stack`.

## Where shared things live

Some files here are not this repo's to edit. Changing them locally only creates drift, which a
weekly workflow reports as an issue.

| To change | Edit it in | It reaches this repo by |
| --- | --- | --- |
| CI behaviour (baseline, validate, release) | `studiobimo/.github`, `.github/workflows/` | the `@v1` tag moving |
| Branch and PR-size rules | `studiobimo/.github`, `.devtools/` | the `@v1` tag moving; lefthook refetches it daily |
| Shared hooks and tool versions | `studiobimo/project-template` | `make -C .devtools sync` |
| Files and blocks listed in the template's `.template/manifest`, and in the manifest of each profile in `.template-profiles` | `studiobimo/project-template` | `make -C .devtools sync` |

A managed block sits between `>>> template:<name>` and `<<< template:<name>` marker lines, like
this section. Edit outside the markers freely; inside them, change the template instead. If a
difference is deliberate, list the path in `.template-ignore` with a comment saying why. Entries
the manifest marks `required` cannot be ignored, and CODEOWNERS sends any change to the override
files to the fleet's owners.
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
