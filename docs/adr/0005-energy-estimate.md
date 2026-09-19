# ADR-0005: Energy-saved estimate methodology

- **Status:** Accepted
- **Date:** 2026-09-18

## Context

The rejoin summary shows the environmental benefit of not leaving the PC on. The numbers must be
honest and traceable to sources.

## Decision

- `kWh = pc_watts × offline_hours / 1000` (default `pc_watts = 150`)
- `CO₂ kg = kWh × 0.394`. This is the EPA avoided-emissions factor,
  [EPA GHG Equivalencies](https://www.epa.gov/energy/greenhouse-gas-equivalencies-calculator-calculations-and-references).
- `water L = kWh × 7.6`. This is the US consumptive average,
  [Alliance for Water Efficiency](https://allianceforwaterefficiency.org/resource/how-much-water-embedded-energy/).
- Tree equivalent: `CO₂ kg / 0.0164`, shown as **tree-days of CO₂ uptake**. The divisor is the EPA's
  60 kg per urban tree seedling over 10 years.

Wording rules:

- every value is prefixed with "≈";
- never say "trees saved";
- the hover text lists the assumptions.

The factors are per-machine settings in `config/tallyhopper.json`, not gamerules.

## Consequences

- The estimates vary widely with hardware and grid mix; defaults are documented and configurable.
- Lifetime totals are recorded as vanilla statistics (`tallyhopper:energy_saved_wh`, `items_credited`).
