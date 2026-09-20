# Contributing

Thanks for helping! This project follows a few strict conventions, and tooling enforces them.

## Setup

1. Install **JDK 25** (e.g. `brew install --cask temurin@25`) and **[uv](https://docs.astral.sh/uv/)**.
2. Run `make -C .devtools setup`. This installs the pinned tools and the `pre-commit`, `commit-msg`
   and `pre-push` hooks.
3. Run `make -C .devtools check` to confirm everything passes.

`make -C .devtools help` lists all targets.

## Project layout

| Path | Purpose |
| --- | --- |
| `common/` | Loader-agnostic code, compiled against vanilla Minecraft only. Most code lives here. |
| `fabric/`, `neoforge/` | Thin loader entry points and `Services` implementations |
| `build-logic/` | Shared Gradle convention plugins |
| `.devtools/` | Makefile, pinned Python tools, hook and guard scripts |
| `docs/` | Roadmap, ADRs, player-facing docs (mirrored to the wiki once the repo is public) |

## Workflow

### Branches: [Conventional Branch](https://conventionalbranch.org/)

`<type>/<description>`, lowercase, with single hyphens. Types: `feature`/`feat`, `bugfix`/`fix`,
`hotfix`, `release`, `chore`, plus `claude`/`codex`/`ai` for agent-authored work.
Examples: `feat/offline-credit`, `fix/backlog-overflow`.

### Commits: [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)

`<type>(<scope>): <summary>`. Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`,
`test`, `build`, `ci`, `chore`, `revert`. Scopes: `common`, `fabric`, `neoforge`, `build`,
`ci`, `docs`, `deps`, `devtools`. Breaking changes use `!` or a `BREAKING CHANGE:` footer.

PRs are **squash-merged**, so the **PR title** must also be a Conventional Commit.
It becomes the commit on `main` that release-please reads.

### Pull requests: at most 20 files

Keep every PR to **20 changed files or fewer**. Split larger work into a **stack**:

```sh
gh extension install github/gh-stack   # once
gh stack init feat/first-slice         # start a stack from main
# ...commit...
gh stack add feat/second-slice         # next layer on top
gh stack submit                        # push all layers and open linked PRs
gh stack sync                          # after a lower layer merges
```

Each layer is measured against the layer below it. The limit is enforced in the `pre-push` hook,
in CI, and for AI agents through a PreToolUse hook (`.devtools/scripts/agent-guard.sh`).

## Code standards

- **Formatting:** palantir-java-format through Spotless. Run `make -C .devtools fmt`; the pre-commit
  hook applies it too.
- **Static analysis:** Error Prone and NullAway fail the build. Our packages are non-null by default;
  annotate nullable values with `@org.jspecify.annotations.Nullable`.
- **Tests:** keep game-independent logic Minecraft-free and unit-test it with JUnit + AssertJ.
  `dev.bimo.tallyhopper.offline` must stay at ≥90% line coverage. In-game behavior is covered by GameTests.
- **Additive only:** code must never delete, replace or extract items the player already has.

## Dependencies

- Build tooling versions live in `gradle/libs.versions.toml`. Minecraft, Fabric and NeoForge
  versions live in `gradle.properties` and are bumped deliberately, one Minecraft version at a time.
- Every configuration is locked (`gradle.lockfile`) and checksum-verified
  (`gradle/verification-metadata.xml`). **After changing any version, run `make -C .devtools lock`**
  and commit the results. Lockfiles are platform-neutral, so it does not matter which OS you
  run `lock` on.
- The few Minecraft libraries that exist only on one OS (the netty native transports and
  `java-objc-bridge`) are listed in `ignoredDependencies` in `build-logic/.../multiloader-common.gradle`.
  They are left out of the lock state on purpose -- see the comment there -- and stay
  checksum-pinned by `verification-metadata.xml`.
- Dependabot opens `chore(deps)` PRs. Gradle bumps also need `make -C .devtools lock` on the PR branch,
  until CI automates it.

## Releases

[release-please](https://github.com/googleapis/release-please) maintains a release PR from the
Conventional Commits on `main`. Merging it tags a SemVer release. Never edit `version` in
`gradle.properties` by hand. Until 1.0.0, breaking changes bump the minor version.

## Repository settings (maintainers)

`main` is protected by org rulesets (studiobimo/.github#2): PRs required, squash-only, linear
history, and required checks `pr-checks`, `ci`, `lint`, `security`.
