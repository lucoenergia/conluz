# Spec — Huawei production pre-aggregation

| | |
| --- | --- |
| **Prefix** | `HPA` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → read by `getMonthlyProduction`, `getYearlyProduction`; rebuilt on demand by `syncMonthlyHuaweiProduction`, `syncYearlyHuaweiProduction` |
| **Related ADRs** | — |
| **Last reviewed against code** | #380 |

## Purpose

The monthly and yearly production totals pre-computed per plant from the hourly production the
Huawei inverters report, so that monthly and yearly production series need not sum the hourly
records on every request. This spec covers only which records a total is summed over; which
periods the scheduled jobs refresh, and when, is not specified yet.

## Glossary

| Term | Meaning |
| --- | --- |
| Local month | A calendar month as the plant's calendar sees it: from local midnight of the 1st to local midnight of the 1st of the next month, in the plant's time zone. In Europe/Madrid, January 2026 is `[2025-12-31T23:00Z, 2026-01-31T23:00Z)` and July 2026 is `[2026-06-30T22:00Z, 2026-07-31T22:00Z)`. |
| Local year | From local midnight of 1 January to local midnight of the next 1 January, in the plant's time zone. |
| Monthly pre-aggregate | The stored total of one plant for one month, one value per quantity, stamped at the start of its local month. |
| Yearly pre-aggregate | The stored total of one plant for one year, one value per quantity, stamped at the start of its local year. |

## Rules

### HPA-001 — A monthly total is the sum of the hourly records of the local month

- **Status:** Active
- **Rule:** When a month is aggregated for a plant, its monthly Huawei production pre-aggregate MUST
  equal the sum of the plant's hourly Huawei production records inside the local month, per field.
  A record of the previous or the next local month MUST NOT be counted.
- **Rationale:** The aggregation used to sum the literal UTC month (`[01T00:00Z, lastT23:59Z]`)
  while stamping the total at local midnight of the 1st. In Europe/Madrid every monthly total
  missed the month's first local hour or two and took the first local hour or two of the next
  month instead, so the total described a period that matched neither its timestamp nor the
  plant's calendar. The Datadis pre-aggregates already sum the local month (DCA-001, DCA-002), so a
  plant's Huawei and Datadis monthly figures now cover the same hours.
- **Source:** #380

### HPA-002 — A yearly total is the sum of the monthly totals of the local year

- **Status:** Active
- **Rule:** When a year is aggregated for a plant, its yearly Huawei production pre-aggregate MUST
  equal the sum of the plant's monthly Huawei production pre-aggregates inside the local year, per
  field. The monthly total of a month of another year MUST NOT be counted.
- **Rationale:** Monthly totals are stamped at local midnight of the 1st, so in Europe/Madrid
  January sits at 23:00Z of the previous year. The aggregation used to sum the literal UTC year
  (`[yyyy-01-01T00:00:00Z, yyyy-12-31T23:59:59Z]`), so every yearly total counted the next year's
  January and missed its own. The same defect in the Datadis production yearly aggregation was
  fixed under DCA-003.
- **Source:** #380

**Scenarios**

```gherkin
Given monthly totals stamped at local midnight in Europe/Madrid for December 2025, January 2026, July 2026, December 2026 and January 2027
When the year 2026 is aggregated
Then the 2026 total is the sum of January, July and December 2026 only
```

## Impossible states

- Two pre-aggregate points for the same plant and period — prevented by the point key: measurement,
  the plant's station code as the only tag, and the local start of the period as the timestamp, so
  re-aggregating replaces the point.
