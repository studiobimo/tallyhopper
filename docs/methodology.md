# How the energy estimate is worked out

When a Tally Hopper credits a session, the rejoin summary can end with a line like:

```text
· ≈1.2 kWh not used (≈0.47 kg CO₂)
```

This page says where those numbers come from, so you can judge how much to trust them. The short
version: they are rough averages, they are configurable, and they describe electricity your computer
did not draw because the world was closed — nothing else.

## What is being estimated

The only thing the mod knows is how long the world was closed. The estimate answers one question:
**if you had left this computer running instead, what would that have cost?** It says nothing about
the server you may be connected to, your screen, your router, or the rest of your day.

The time used is the credited window, not the raw gap. If the `tallyhopper:maxOfflineHours` gamerule
cuts a long absence, the estimate is cut with it.

## The formulas

With `h` the credited hours and the factors from `config/tallyhopper.json`:

| Value | Formula | Default factor |
| --- | --- | --- |
| Electricity | `kWh = pc_watts × h / 1000` | `pc_watts = 150` |
| Emissions | `kg CO₂ = kWh × grid_kg_co2_per_kwh` | `0.394` |
| Water | `L = kWh × water_liters_per_kwh` | `7.6` |
| Tree-days | `kg CO₂ / 0.0164` | — |

## Where the defaults come from

- **150 W** is a middle-of-the-road desktop idling a game, not a laptop and not a gaming tower under
  load. Yours is probably different; measure it if you care about the number.
- **0.394 kg CO₂/kWh** is the EPA's avoided-emissions factor for US grid electricity, from the
  [greenhouse gas equivalencies calculator][epa].
- **7.6 L/kWh** is a US consumptive-use average for generating electricity, via the
  [Alliance for Water Efficiency][awe].
- **0.0164 kg CO₂/day** is one urban tree seedling taking up 60 kg over 10 years, also the EPA's
  figure: `60 / (10 × 365.25)`.

## Why it says "tree-days"

A tree takes up carbon slowly and for decades. Saying "you saved half a tree" would be meaningless,
so the summary reports **days of CO₂ uptake by one urban tree** instead, and every figure is prefixed
with "≈". The hover text on the summary repeats the assumptions in use.

## Changing the factors

They are per-machine, not per-world, so they live in `config/tallyhopper.json` rather than in
gamerules:

```json
{
  "pc_watts": 150.0,
  "grid_kg_co2_per_kwh": 0.394,
  "water_liters_per_kwh": 7.6,
  "show_energy_estimate": true
}
```

Use your own hardware's draw and your own grid's intensity if you know them — most national grids are
well away from the US average. Setting `show_energy_estimate` to `false` drops the line from the
summary entirely; the `tallyhopper:energy_saved_wh` statistic keeps counting either way.

A file that can't be read is left alone and the defaults are used, so a typo never costs you your
settings.

## Limits

- Every factor is an average over a whole country's grid and a whole class of hardware.
- Real idle draw swings with hardware, power settings and what else the machine is doing.
- Water and CO₂ intensity vary by region, by season and by time of day.
- The estimate is a conversation starter about leaving machines on, not an accounting figure.

The decision record behind this is [ADR-0005](adr/0005-energy-estimate.md).

[epa]: https://www.epa.gov/energy/greenhouse-gas-equivalencies-calculator-calculations-and-references
[awe]: https://allianceforwaterefficiency.org/resource/how-much-water-embedded-energy/
