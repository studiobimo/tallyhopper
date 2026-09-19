# Tally Hopper

A Minecraft mod that lets you turn your computer off at night instead of leaving an AFK farm running.

> **Status: pre-alpha.** Under active development. See the [roadmap](docs/ROADMAP.md).

## How it works

1. **Measure.** Craft a Tally Hopper (`hopper + clock`), or use a clock on a hopper already in your farm.
   It counts the items flowing through it and learns your farm's real output rate.
2. **Leave.** Close your world. The mod records the real-world time.
3. **Return.** When you reopen the world, the hopper credits `rate × time away`. Items go into the
   chest it feeds, and anything that doesn't fit waits in the hopper and drains normally.

It is **strictly additive**. It never removes, replaces or rearranges items you already have.
A default 24-hour cap applies, and you get an estimate of the energy you saved.

| Requirement | Version |
| --- | --- |
| Minecraft | 26.3 (Java Edition) |
| Loaders | Fabric (with Fabric API) · NeoForge |
| Java | 25 |
| License | [MIT](LICENSE) |

## Why not a datapack?

Datapacks can't read the real-world clock, and they get no signal when the world closes.
The reasoning is in [ADR-0001](docs/adr/0001-mod-not-datapack.md).

## Development

Requirements: JDK 25 (`brew install --cask temurin@25`) and [uv](https://docs.astral.sh/uv/).

```sh
make -C .devtools setup               # pinned tools + git hooks
make -C .devtools check               # lint + build + tests (same as CI)
make -C .devtools run-fabric          # dev client (Fabric)
make -C .devtools run-neoforge        # dev client (NeoForge)
```

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a PR.
