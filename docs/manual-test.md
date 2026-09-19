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

1. Feed the Tally Hopper from a working farm, or drop stacks into it by hand at a steady pace.
2. Open the screen.
3. **Expect:** the title, the five slots, a "Calibrating: _n_ of 1 min" line, the delivery mode line,
   and the override box above the inventory label with nothing overlapping it.
4. Wait out the minute and reopen it.
5. **Expect:** "Ready", one line per item with a plausible per-hour rate, and — for a hopper that
   feeds a line — where that line ends.
6. Type `cobblestone 600` into the box and press Enter.
7. **Expect:** a chat answer, and the rate line reading "600/h (by hand)" on the next open.
8. Type `clear` and press Enter.
9. **Expect:** the override is gone and the measured rate is back.

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

1. Break a Tally Hopper that is holding a backlog.
2. **Expect:** the dropped item's tooltip lists the carried backlog.
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
