# ADR-0006: A screen built from vanilla parts, and what calibration costs

- **Status:** Accepted
- **Date:** 2026-09-19

## Context

The first Tally Hopper screen was a panel of text: a status line, the delivery mode, one line per item
rate, and a text box for setting a rate by hand. It worked, but it read like a debug overlay rather
than a Minecraft screen, and reserving room for the longest possible text left a large empty gap in
every ordinary case.

Minecraft already has widgets for all three things this screen needs to say.

## Decision

Build the screen out of vanilla parts, and keep the panel at the ordinary 176×166.

- **Calibration progress** is the villager screen's experience bar. It fills white-yellow while the
  hopper watches (the white `experience_bar_result` sprite with a yellow tint) and turns green
  (`experience_bar_current`) when the rate can be trusted.
- **The sapling a calibration run costs** sits in the brewing stand's apparatus, blitted out of
  vanilla's own `textures/gui/container/brewing_stand.png` at runtime rather than copied into this
  mod: the blaze slot's frame becomes the sapling slot, and the coil, bubble trail, base and fuel
  groove come with it. The `fuel_length` meter fills that groove with the saplings left, and the
  `bubbles` sprite rises while the hopper is watching, exactly as it does while a potion brews. A
  resource pack restyles this screen along with the brewing stand.
- **State** is vanilla's `LockIconButton`: open while the hopper is still watching, closed once it is
  ready. Pressing it spends a sapling and measures again from nothing, through
  `AbstractContainerMenu#clickMenuButton`, the same path the lectern and the stonecutter use.
- **The numbers** — rates, mode, backlog, last credit, where the hopper line ends — are a tooltip on
  the bar, not text on the panel.
- **Setting a rate by hand** stays `/tallyhopper rate`, so permission checks live in one place.

Calibration costs one sapling per run. A hopper that has never been paid for watches nothing and says
so; a hopper that is already measuring keeps earning without further cost. The sapling slot is a
container of its own, never a sixth hopper slot, so vanilla hopper logic still sees exactly five and
can neither push a sapling out nor pull one in. It spills when the block breaks.

## The lock does not freeze the rate

The obvious reading of a padlock is "hold this number". We deliberately did not do that.

The credited rate is a rolling window of the last hour of *running* time. That window is the only
defence the mod has against a player hand-feeding a chest of items through a hopper during
calibration to inflate its rate: if they keep playing, the window rolls and the measurement returns to
what the farm actually produces. Freezing the rate at calibration time would make that inflation
permanent, and re-doing it would cost one sapling.

So the padlock reports whether the hopper has a settled rate, and is the button for measuring again.
The hopper keeps watching in the background either way, and credit keeps using the rolling window.

## Consequences

- A Tally Hopper is useless until a sapling is fed to it, which must be discoverable: the screen says
  "Needs a sapling" and the empty meter's tooltip says what goes there.
- The panel shows less at a glance. Anything a player needs beyond the state is one hover away.
- Breaking the block must drop the sapling slot as well as the five hopper slots; the mod's additive
  rule covers items a player put in, not only items it credited.
- Three of the GameTests that measure a farm now stock a sapling first, as a player would.
