# Roadmap

Every PR changes at most 20 files. A milestone is usually delivered as one `gh stack`.
A milestone is done only when all of its **exit criteria** are met.

## M0: Foundation

- [ ] Org CI building blocks in `studiobimo/.github`: #1 (make public), #3–#9 (reusable workflows, actions)
- [x] Multiloader Gradle scaffold (MultiLoader-Template @26.3), `dev.bimo.tallyhopper`
- [x] Fabric and NeoForge entry points
- [x] Spotless, Error Prone + NullAway, JUnit + AssertJ, JaCoCo gate
- [x] Dependency locking and sha256 dependency verification
- [x] `.devtools/` (Makefile, uv, pre-commit), agent guards for Claude Code and Codex
- [x] `.java-version` as the single Java version source
- [x] Governance: README, CONTRIBUTING, CODEOWNERS, templates, Dependabot, release-please config, ADRs
- [ ] CI wrappers calling the org reusable workflows (`pr-checks`, `ci`, `lint`, `security`, `release-please`)
- [ ] Dependabot Gradle PRs refresh the lockfiles and verification metadata automatically
- [ ] Wiki sync (once the repository is public)

### Exit criteria

- [x] An empty mod loads on Fabric and NeoForge dev servers
- [ ] `make -C .devtools check` passes locally **and in CI**
- [x] Hooks reject a bad commit message and a bad branch name, locally and from a Claude/Codex tool call
- [x] Hooks reject a PR over 20 files, locally and from a Claude/Codex tool call
- [ ] CI rejects all three of the above
- [ ] release-please opens a release PR

## M1: Core logic (pure Java)

- [x] `SessionClock` math: heartbeat, offline window, clamping
- [x] `RateTracker`: rolling buckets, pause detection, warm-up gate
- [x] `OfflineCredit`: carry, caps, saturating arithmetic
- [x] `Backlog`: `item → long count` with a cap

### Exit criteria

- [x] JUnit covers: 1000/h × 8 h = 8000, fractional carry, negative clock jumps, 100-day gaps,
      cap hits, override precedence, and the warm-up gate
- [x] ≥90% line coverage on `dev.bimo.tallyhopper.offline`

## M2: Block parity

- [x] Block, block entity and registration on both loaders
- [x] Model and textures (clock face tinted calibrating/ready), lang
- [x] Datagen: recipe `hopper + clock + any sapling` (the clock is kept), loot table
- [x] Use a clock on a hopper to convert it in place (costs a sapling); sneak-use converts the end of the chain

### Exit criteria

- [x] GameTests show vanilla hopper parity on both loaders: push, pull, pickup, redstone lock, comparator
- [x] Conversion keeps contents and facing
- [x] Sneak-use finds the correct end hopper on a 10-hopper chain and stops on loops

## M3: Measurement

- [x] Intake counting, pause detection, and the default-components-only rule
- [x] Per-hopper rate override, basic `/tallyhopper info`

### Exit criteria

- [x] A GameTest dispenser-clock "farm" is measured within ±5% of its true rate
- [x] Items inserted through the GUI are not counted

## M4: Offline credit and delivery

- [x] Heartbeat and lifecycle hooks, eligibility, lazy credit
- [x] `ItemSink` for Fabric Transfer and NeoForge `ResourceHandler` (both already wrap vanilla containers, so there is no separate `Container` path)
- [x] Visible-slot refill from the backlog; terminal and line modes

### Exit criteria (GameTests)

- [x] Credit fills a (double) chest; existing items are untouched
- [x] A full target sends everything to the backlog; hopper minecarts drain it
- [x] Line mode flows through a vanilla item sorter without jamming filter hoppers (tested on a hopper line: credit arrives one item at a time at vanilla speed and never bulk-fills a hopper; no redstone sorter is built)
- [x] No double credit across restarts; a force-loaded hopper is eligible, an unloaded one is not (restarts are simulated by saving and reloading the block entity)
- [x] 100 days at 1e6 items/h: no crash, save size bounded
- [x] Breaking the block preserves the backlog on the item

## M5: UX

- [x] GUI with network sync, built from vanilla widgets: calibration bar, sapling slot and meter,
  padlock to measure again; rates, mode, drain estimate and chain analysis on hover ([ADR-0006](adr/0006-screen-from-vanilla-parts.md))
- [x] Rejoin chat summary with energy/CO₂/water/tree-day estimate; custom statistics
- [x] `config/tallyhopper.json`, gamerules, tooltip, block-state visuals, advancement
- [x] Methodology page for the environmental estimate

### Exit criteria

- [x] A Fabric client GameTest screenshots the GUI in the calibrating and ready states
- [x] All strings are in `en_us.json`
- [ ] The [manual test script](manual-test.md) passes on both loaders

## M6: Compatibility

- [x] Modded storage through the transfer APIs; chunk-loader scenarios
      ([compatibility](compatibility.md))

### Exit criteria

- [x] Credit lands in at least one popular modded storage block on each loader (documented):
      Storage Drawers 26.3.0.0 on Fabric and NeoForge, under GameTest

## M7: Release 0.1.0 (beta)

- [ ] README with a how-it-works diagram, placement guidance and limits
- [ ] Modrinth and CurseForge pages, screenshots

### Exit criteria

- [ ] A tag publishes provenance-attested jars to Modrinth, CurseForge and GitHub automatically

**1.0.0:** two releases stable on a Minecraft version, with no open P1 bugs.

## Backlog (v2)

Jade/WTHIT overlay, a dedicated-server mode for time a chunk is unloaded, rate-history graph.
