# Spec — Energy values in consumption and energy metrics responses

| | |
| --- | --- |
| **Prefix** | `EVL` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → operations `getSupplyDailyConsumption`, `getSupplyMonthlyConsumption`, `getSupplyEnergyMetrics`, `getMembershipMonthlyConsumption`, `getMembershipEnergyMetrics`, `getMembershipHourlyProfile` |
| **Related ADRs** | — |
| **Last reviewed against code** | #460 |

## Purpose

The energy figures, in kWh, that the consumption series and the energy metrics report for a supply
or a membership. They are sums of the hourly records Datadis publishes, which are stored as floating
point numbers. This spec covers the precision those figures are reported with and where in the
calculation they are rounded, the names and descriptions each quantity carries across operations,
and how assigned production is derived. The rest of the capability is specified lazily, as issues
touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| kWh value | An energy figure reported in kWh by one of the operations of this spec. It is a stored value, a sum of stored values, or a figure derived from such sums. |
| Response mapping | The last step of an operation: turning the computed result into the response it returns. Nothing computed from a value happens after it. |
| Grid import | Energy a supply took from the grid; it excludes self-consumed energy. Field `gridImportKWh`. |
| Self-consumption | Energy the community produced and the supply consumed, as reported by the distributor. Field `selfConsumptionKWh`. |
| Surplus | Energy assigned to the supply and fed back to the grid. Field `surplusKWh`. |
| Total consumption | Energy the supply consumed, whatever its origin. Field `totalConsumptionKWh`. Derived: grid import + self-consumption. |
| Assigned production | Energy the community assigned to the supply, whether consumed or fed back. Field `assignedProductionKWh`. Derived: self-consumption + surplus. |
| Average and sample count | An average of a quantity is named `average` followed by the quantity's field name (`averageTotalConsumptionKWh`); the number of records it rests on is named after the quantity with `SampleCount` in place of `KWh` (`totalConsumptionSampleCount`). |
| Datadis pass-through schema | A schema that carries the distributor's own record shape and names (`DatadisConsumption`, `DatadisProduction`, the hourly consumption CSV report). It keeps the Datadis names, which the quantities above do not use. |

## Rules

### EVL-001 — kWh values are rounded to 3 decimals at response mapping

- **Status:** Active
- **Rule:** kWh values in `getSupplyDailyConsumption`, `getSupplyMonthlyConsumption`,
  `getSupplyEnergyMetrics`, `getMembershipMonthlyConsumption` and `getMembershipEnergyMetrics` MUST
  be rounded to 3 decimals, half-up, at response mapping. Stored values MUST NOT be rounded.
- **Rationale:** Summing floating-point values leaves storage noise behind (`0.1 + 0.2` reads
  `0.30000000000000004`; the membership monthly series returned figures such as `161.308001`). That
  noise leaked into the API, so every client had to round, and equality checks between endpoints
  were fragile. 3 decimals is the resolution the distributor reports in, so no measured information
  is lost. Rounding stays out of storage so the stored records keep what Datadis published.
- **Source:** #385

**Scenarios**

```gherkin
Given a supply with hourly records of 0.1234 and 0.2222 kWh of grid import on one day
When its daily series is read for that day
Then the day's grid import is 0.346 kWh
And the stored hourly records still carry 0.1234 and 0.2222 kWh
```

### EVL-002 — Sums, ratios and savings are computed from unrounded values

- **Status:** Active
- **Rule:** Sums across supplies or periods, ratios and savings MUST be computed from unrounded
  values. Rounding MUST happen once, on the final value.
- **Rationale:** Summing rounded values accumulates rounding error and makes a result depend on
  the order it was aggregated in. Rounding the final value only also keeps the membership monthly
  series and the membership energy metrics in agreement for a month that both have read in full.
- **Source:** #385

**Scenarios**

```gherkin
Given a membership with two supplies, one with 0.1234 kWh of grid import in a month and the other with 0.2222 kWh
When the membership monthly series is read for that month
Then the month's grid import is 0.346 kWh, not 0.123 + 0.222 = 0.345 kWh
```

### EVL-003 — A quantity has one field name and one description across operations

- **Status:** Active
- **Rule:** A quantity MUST have the same field name and description in every operation that
  exposes it, as listed in the glossary; a description MAY be adapted only to its subject, its
  period and, for an average, to averaging. Averages and sample counts MUST carry the quantity's
  name. Datadis pass-through schemas are exempt and keep the Datadis names.
- **Rationale:** The consumption series used the distributor's names (`consumptionKWh`,
  `surplusEnergyKWh`, `selfConsumptionEnergyKWh`) while the energy metrics named the same
  quantities `gridImportKWh`, `surplusKWh` and `selfConsumptionKWh`, and the hourly profile called
  total consumption `averageConsumptionKWh`. A client showing two operations side by side had to
  map names by hand, and `consumptionKWh` read as total consumption when it is grid import.
  Pass-through schemas keep the distributor's names because they speak its record shape.
- **Source:** #460

**Scenarios**

```gherkin
Given a supply with stored hourly records
When its daily series and its energy metrics are read
Then both report its grid import as gridImportKWh, described as energy taken from the grid
And its hourly series still reports it as consumptionKWh
```

### EVL-004 — Assigned production is derived from self-consumption and surplus

- **Status:** Active
- **Rule:** Assigned production MUST be derived as self-consumption plus surplus, by one shared
  derivation, wherever it is exposed. The distributor's generation field MUST NOT be exposed as
  assigned production.
- **Rationale:** The consumption series exposed the distributor's `generation_energy_kwh` while the
  energy metrics and the hourly profile derived assigned production from self-consumption and
  surplus, so the series and the metrics disagreed for the same month. One derivation, computed from
  unrounded values and rounded once (EVL-002), keeps every operation in agreement.
- **Source:** #460

**Scenarios**

```gherkin
Given a supply with hourly records whose stored generation is 9.999 kWh, with 0.1234 kWh of self-consumption and 0.2222 kWh of surplus in total on one day
When its daily series is read for that day
Then the day's assigned production is 0.346 kWh, not 0.123 + 0.222 = 0.345 kWh
And the day carries no generation figure
```
