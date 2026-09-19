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
2. **Expect:** "Needs a sapling", an empty bar, an empty sapling slot on the left and an open padlock.
   Hovering the empty meter says what goes there.
3. Put a stack of saplings in the slot.
4. **Expect:** one sapling is taken straight away, the meter under the stand fills to match what is
   left, the bubbles start rising, and the status becomes "Calibrating".
5. Feed the hopper from a working farm, or drop stacks in by hand at a steady pace, and watch the bar.
6. **Expect:** the bar fills yellow as the minute passes, then turns green, the bubbles stop and the
   padlock closes.
7. Hover the bar.
8. **Expect:** a tooltip with the status, one line per item with a plausible per-hour rate, the
   delivery mode and — for a hopper feeding a line — where that line ends.
9. Run `/tallyhopper rate set ~ ~ ~ minecraft:cobblestone 600` at the hopper and hover the bar again.
10. **Expect:** the rate line reads "600/h (by hand)". `/tallyhopper rate clear` puts the measured one
    back.
11. Press the padlock.
12. **Expect:** another sapling is spent, the bar empties and calibration starts over. With an empty
    sapling slot the padlock is greyed out and pressing it does nothing.

## 3. Offline credit

1. Note what the hopper's target container holds.
2. Quit to the title screen, close the game, and wait a few real minutes.
3. Start the game and rejoin the world.
4. **Expect:** one chat line summarising what was earned, with the details on hover for several
   hoppers, and the energy line if `show_energy_estimate` is on.
5. **Expect:** the target container gained items at roughly the measured rate for the time away, and
   nothing that was already there was moved, replaced or removed.
6. Reopen the screen.
7. **Expect:** either a "Last credit" line, or a backlog line with a drain estimate.
8. Rejoin a second time without waiting.
9. **Expect:** no summary and no second credit for the same absence.

## 4. Delivery modes

1. **Terminal:** point the Tally Hopper straight at a chest.
   **Expect:** the chest fills at once on rejoin.
2. **Line:** point it into a chain of vanilla hoppers.
   **Expect:** items leave at hopper speed and arrive at the end of the chain; the backlog on the
   screen goes down as they do.
3. Fill the destination completely and rejoin again.
   **Expect:** the summary says items were not created because the backlog is full; **nothing is
   voided and nothing already stored is touched**.

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
4. Open Statistics.
   **Expect:** "Items credited while away" and "Energy not used (Wh)" have grown.
5. Open Advancements.
   **Expect:** "Sleep Mode" was granted the first time credit was applied.

## 7. Presentation

1. Check the block from a distance.
   **Expect:** its clock face reads orange while calibrating and green once ready.
2. Hover the item in the creative inventory and in a chest.
3. Switch the language to something other than English.
   **Expect:** no raw keys such as `gui.tallyhopper.status.ready` anywhere on screen — everything
   falls back to English text.

## Reporting

Note the loader, the version, and any step whose **Expect** did not happen, with the log lines around
it. A step that failed on one loader and passed on the other is worth saying so explicitly.
