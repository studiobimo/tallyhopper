# Manual test script

What to run by hand before a release, on **both loaders**. The automated tests cover the maths, the
credit rules and the screen; this script covers what only a person at a keyboard notices — that the
world reacts the way the mod promises.

Run it twice, once per loader:

```sh
make -C .devtools run-fabric
```

```sh
make -C .devtools run-neoforge
```

Use a fresh creative world each time, and keep the game's log open for warnings.

## 0. Setup

Shorten calibration so the script doesn't take half an hour:

```text
/gamerule tallyhopper:min_observation_minutes 1
```

Record the loader, the mod version and the world name in your notes.

## 1. Conversion

1. Place a hopper over a chest, put a clock and any sapling in your hand.
2. Right-click the hopper with the clock.
3. **Expect:** the hopper becomes a Tally Hopper, the sapling is consumed, the clock is not, and
   nothing in the hopper or the chest changed.
4. Point at a hopper in the middle of a chain and convert it.
5. **Expect:** a message naming where the chain ends, and no conversion of the block you clicked.
6. Make a hopper loop and try again.
7. **Expect:** the "chain loops back on itself" message, and nothing is converted.

## 2. Calibration and the screen

1. Open the screen of a freshly placed Tally Hopper.
2. **Expect:** "Needs a sapling", an empty bar, a ghost sapling in the empty slot on the left and an
   open padlock. Hovering the empty meter says what goes there.
3. Put a stack of saplings in the slot.
4. **Expect:** one sapling is taken straight away, the meter under the stand fills to match what is
   left, the bubbles start rising, and the status becomes "Calibrating".
5. Feed the hopper from a working farm — a dispenser on a clock does — and watch the bar. Throwing
   stacks in by hand will not do: that is deliberately not counted.
6. **Expect:** the bar fills yellow as the minute passes, then turns green, the bubbles stop and the
   padlock closes.
7. Stand over the hopper and throw a stack in with the drop key, then throw one out of the hopper's
   own screen.
8. **Expect:** both are picked up as a vanilla hopper would, the hopper's five slots hold them, and
   no rate line appears for them. Mining a block over the hopper still counts.
9. Hover the bar.
10. **Expect:** a tooltip with a coloured status heading (green ready, yellow calibrating, grey
    passthrough), one white line per item with a plausible per-hour rate, hand-set rates in aqua, and
    the delivery mode and chain end in dark grey italics under them.
11. Run `/tallyhopper rate set ~ ~ ~ minecraft:cobblestone 600` at the hopper and hover the bar again.
12. **Expect:** the rate line reads "600/h (by hand)". `/tallyhopper rate clear` puts the measured one
    back.
13. Press the padlock.
14. **Expect:** another sapling is spent, the bar empties and calibration starts over. With an empty
    sapling slot the padlock is greyed out and pressing it does nothing.

## 3. Offline credit

1. Note what the hopper's target container holds.
2. Quit to the title screen, close the game, and wait a few real minutes.
3. Start the game and rejoin the world.
4. **Expect:** two chat lines: a gold `[Tally Hopper]` tag, then what was earned in grey with the
   item count in green, and underneath it in dark grey how it landed — delivered, backlogged, and the
   energy line if `show_energy_estimate` is on. With several hoppers, hovering the first line lists
   them.
5. **Expect:** the target container gained items at roughly the measured rate for the time away, and
   nothing that was already there was moved, replaced or removed.
6. Reopen the screen.
7. **Expect:** either a "Last credit" line, or a backlog line with a drain estimate.
8. With a backlog still draining, look at the status line and the padlock.
9. **Expect:** the status reads "Paused: backlog draining" with no rising bubbles, and the padlock is
   greyed out; hovering it says the backlog has to drain first. The bar holds the progress it had
   rather than restarting at zero.
10. Wait for the backlog to reach zero.
11. **Expect:** the padlock is pressable again, and pressing it spends a sapling and starts a new run.
12. Rejoin a second time without waiting.
13. **Expect:** no summary and no second credit for the same absence.

## 4. Delivery modes

1. **Terminal:** point the Tally Hopper straight at a chest.
   **Expect:** the chest fills at once on rejoin.
2. **Line:** point it into a chain of vanilla hoppers.
   **Expect:** items leave at hopper speed and arrive at the end of the chain; the backlog on the
   screen goes down as they do.
3. Fill the destination completely and rejoin again.
   **Expect:** the summary says items were not created because the backlog is full; **nothing is
   voided and nothing already stored is touched**.
4. **Chained:** place a second Tally Hopper on the same line, upstream of the first.
   **Expect:** a gold chat line on placement saying it passes items through without earning; the
   upstream screen's status reads "Passthrough" in a lighter grey, the bar goes flat grey, the padlock
   is gone entirely, the sapling slot is no longer asked for, and on rejoin the line delivers one
   farm's output, not two.
5. Try to convert a third hopper upstream of those two.
   **Expect:** the conversion is refused and no sapling is spent.
6. Break the downstream Tally Hopper and reopen the upstream one's screen.
   **Expect:** it goes back to its normal status and the padlock returns.

## 5. Breaking and placing

1. Break a Tally Hopper that is holding a backlog and some saplings.
2. **Expect:** the dropped item's tooltip lists the carried backlog, and the saplings drop as items.
3. Place it somewhere else.
4. **Expect:** the backlog is still there and drains into the new target.
5. Break one in creative with an empty hand.
6. **Expect:** the item drops rather than vanishing.

## 6. Gamerules, statistics and the advancement

1. `/gamerule tallyhopper:rejoin_summary false`, then rejoin after an absence.
   **Expect:** credit still happens, no chat line.
2. `/gamerule tallyhopper:max_offline_hours 1` and rejoin after a longer gap.
   **Expect:** the summary says it was capped.
3. `/gamerule tallyhopper:backlog_cap 10` on a busy hopper.
   **Expect:** the summary reports items not created once the cap is hit.
4. Hover the bar on a hopper that became ready only a few minutes ago.
   **Expect:** a line saying it credits at a percentage until an hour is watched, and rates that match
   that percentage. After an hour of running, the line is gone and the rates are the measured ones.
5. `/gamerule tallyhopper:max_items_per_hour 100`, then rejoin after an absence.
   **Expect:** no hopper credits more than 100 items an hour, whatever it measured or was overridden
   to. Put it back to 9000 afterwards.
6. Open Statistics.
   **Expect:** "Items credited while away" and "Energy not used (Wh)" have grown.
7. Open Advancements.
   **Expect:** "Sleep Mode" was granted the first time credit was applied.

## 7. Presentation

1. Check the block from a distance.
   **Expect:** its brass clock face reads cream while calibrating and green once ready, on all four
   sides, with the hand the same way up on each.
2. Feed it a few items, by day and by night.
   **Expect:** the red lamp beside the clock face flashes as it counts items, like an observer,
   with a faint pale-red spill on the rim around it. At night the lamp glows without lighting up
   the ground. Thrown and hand-fed items don't flash it, and it never stays lit.
3. Open the screen.
   **Expect:** the panel has the black rounded outline of every vanilla container.
4. Hover the item in the creative inventory and in a chest.
   **Expect:** the icon is a hopper with a small pocket-watch badge.
5. Switch the language to something other than English.
   **Expect:** no raw keys such as `gui.tallyhopper.status.ready` anywhere on screen — everything
   falls back to English text.

## 8. Modded storage and chunk loading

Storage Drawers is already in the dev runtime on both loaders (see [compatibility](compatibility.md)).

1. Point a calibrated Tally Hopper at a drawer and rejoin after an absence.
   **Expect:** the credit lands in the drawer, which a vanilla hopper could not fill at all.
2. Put something else in the drawer first, then rejoin.
   **Expect:** the drawer keeps what it held, nothing is replaced, and the credit waits in the
   backlog instead.
3. `/forceload add ~ ~` on a hopper's chunk, travel far away, then quit and rejoin.
   **Expect:** that hopper credits normally.
4. Leave another hopper in a chunk nobody loads, quit for a while, rejoin, and then walk to it.
   **Expect:** nothing is credited for the time it was asleep, and its own summary line appears only
   if it had been running when the world closed.

## Reporting

Note the loader, the version, and any step whose **Expect** did not happen, with the log lines around
it. A step that failed on one loader and passed on the other is worth saying so explicitly.
