# Compatibility

What a Tally Hopper can deliver into, and what has to be loaded for it to work at all.

## Modded storage

A Tally Hopper never writes into a container directly. It asks the loader's transfer API for whatever
storage sits in front of it, and inserts through that:

| Loader   | API                                                         |
| -------- | ----------------------------------------------------------- |
| Fabric   | `ItemStorage.SIDED` (Fabric Transfer API)                   |
| NeoForge | `Capabilities.Item.BLOCK` (`ResourceHandler<ItemResource>`) |

Both APIs already wrap every vanilla container — respecting its sided insertion rules and joining the
halves of a double chest — so there is no separate vanilla path to keep in step, and any mod that
registers storage the normal way is reached for free.

Three rules hold regardless of what the storage is:

- **Insertion only.** Nothing is ever moved, replaced or taken out. A storage block that already holds
  something keeps exactly what it held.
- **Partial insertion is normal.** Whatever the storage refuses goes to the hopper's backlog and is
  offered again later, at hopper speed.
- **Default components only.** A Tally Hopper measures and credits plain items, so nothing it delivers
  carries enchantments, custom names or other data.

### Verified

| Mod                                                        | Loaders          | Version tested | Result                      |
| ---------------------------------------------------------- | ---------------- | -------------- | --------------------------- |
| [Storage Drawers](https://modrinth.com/mod/storagedrawers) | Fabric, NeoForge | 26.3.0.0       | Credit lands; see below     |
| Vanilla chests, barrels, shulker boxes, hoppers, droppers  | Fabric, NeoForge | 26.3           | Covered by the credit tests |

Storage Drawers is the mod the automated tests run against, because a drawer is as unlike a chest as
storage gets: its block entity is not a `Container` at all, so a vanilla hopper cannot see it and only
the transfer API can reach it; one drawer slot holds thousands of items instead of a stack; and a
drawer that already holds something refuses every other item. All three behave correctly —

- a single drawer slot takes thousands of items of credit at once;
- a drawer that fills up refuses the rest, which waits in the backlog exactly as it would for a full
  chest;
- a drawer holding something else refuses the credit outright and keeps what it holds.

The mod is loaded into the dev and GameTest runtimes of both loaders (see each loader's
`build.gradle`) and is never on the compile classpath. `CompatibilityTests` finds its block by id and
never names one of its classes, so the tests do not depend on the mod's API and keep working across
its versions.

### Adding another mod to the matrix

1. Add it to `storage_drawers_version`'s neighbours in `gradle.properties` and to the runtime-only
   dependency in each loader's `build.gradle`.
2. Run `make -C .devtools lock` so the jar is locked and checksum-verified.
3. Point `CompatibilityTests` at its block id, or add a test beside the existing ones.
4. Record the result in the table above, including a version, and say so if it failed.

## Chunk loading

A Tally Hopper is an ordinary block entity: it measures and credits only while its chunk is *ticking*.
The mod never loads a chunk itself, and never will — a mod that force-loaded every hopper would turn a
storage block into a performance decision.

That gives three rules worth knowing:

- **A chunk loader decides which hoppers are awake.** Vanilla `/forceload`, the spawn chunks, or a
  mod's chunk loader all work the same way: a hopper in a ticking chunk keeps measuring while you are
  elsewhere in the world, and is eligible for credit when you next open the world.
- **Loaded is not the same as ticking.** Chunks at the edge of a player's view distance are loaded but
  do not tick their block entities. A hopper there is asleep as far as the mod is concerned.
- **Credit is lazy.** A hopper whose chunk is not loaded when the world opens is credited the moment
  its chunk does load, however much later that is, and gets its own rejoin summary line rather than
  being credited in silence.

A hopper that was already asleep when the world closed — its chunk unloaded more than
`OfflineWindow.ELIGIBILITY_GRACE` (two minutes) before the final heartbeat — earns nothing for that
session, and walking back to it later does not produce a delayed payout. Without that rule, leaving
Tally Hoppers scattered around the world and touring them would pay out for farms that were never
running. See [ADR-0003](adr/0003-measured-rate-model.md).

## Not yet covered

- **Time a chunk spends unloaded on a dedicated server.** A hopper on a server that stays up is
  credited for nothing, because the world was never closed; a server-side "the chunk was asleep" mode
  is on the v2 backlog in the [roadmap](ROADMAP.md).
- **Storage that reports capacity it does not have.** The hopper believes what the transfer API tells
  it accepted. A storage block that lies about an insert would lose the difference; nothing vanilla or
  in the matrix above does this.
