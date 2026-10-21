# Spec — Energy values in consumption and energy metrics responses

| | |
| --- | --- |
| **Prefix** | `EVL` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → operations `getSupplyDailyConsumption`, `getSupplyMonthlyConsumption`, `getSupplyEnergyMetrics`, `getMembershipMonthlyConsumption`, `getMembershipEnergyMetrics` |
| **Related ADRs** | — |
| **Last reviewed against code** | #385 |

## Purpose

The energy figures, in kWh, that the consumption series and the energy metrics report for a supply
or a membership. They are sums of the hourly records Datadis publishes, which are stored as floating
point numbers. This spec covers only the precision those figures are reported with, and where in
the calculation they are rounded. The rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| kWh value | An energy figure reported in kWh by one of the operations of this spec. It is a stored value, a sum of stored values, or a figure derived from such sums. |
| Response mapping | The last step of an operation: turning the computed result into the response it returns. Nothing computed from a value happens after it. |

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
