# Spec — Huawei production pre-aggregation

| | |
| --- | --- |
| **Prefix** | `HPA` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → read by `getMonthlyProduction`, `getYearlyProduction`; rebuilt on demand by `syncMonthlyHuaweiProduction`, `syncYearlyHuaweiProduction` |
| **Related ADRs** | — |
| **Last reviewed against code** | #384 |

## Purpose

The monthly and yearly production totals pre-computed per plant from the hourly production the
Huawei inverters report, so that monthly and yearly production series need not sum the hourly
records on every request. This spec covers which records a total is summed over and which periods
the daily aggregation run refreshes; the rest of the capability is specified lazily, as issues touch
it.

## Glossary

| Term | Meaning |
| --- | --- |
| Local month | A calendar month as the plant's calendar sees it: from local midnight of the 1st to local midnight of the 1st of the next month, in the plant's time zone. In Europe/Madrid, January 2026 is `[2025-12-31T23:00Z, 2026-01-31T23:00Z)` and July 2026 is `[2026-06-30T22:00Z, 2026-07-31T22:00Z)`. |
| Local year | From local midnight of 1 January to local midnight of the next 1 January, in the plant's time zone. |
| Monthly pre-aggregate | The stored total of one plant for one month, one value per quantity, stamped at the start of its local month. |
| Yearly pre-aggregate | The stored total of one plant for one year, one value per quantity, stamped at the start of its local year. |
| Huawei sync window | The days the daily sync of previous days re-reads from Huawei on every run: from seven days before the run's date through the day before it, both inclusive. Today is synced by its own hourly job. |
| Re-aggregation range | From the first day of the Huawei sync window through the current local day, both inclusive. A month or year belongs to it when any of its days does. The current day is the run's date in the plant's time zone. |
| Daily aggregation run | One run of the scheduled monthly job (02:00) followed by the scheduled yearly job (03:00), over the plant of every enabled Huawei configuration. |

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

### HPA-003 — The pre-aggregates are current for every period of the re-aggregation range

- **Status:** Active
- **Rule:** After each daily Huawei aggregation run, the monthly and yearly Huawei production
  pre-aggregates for every period overlapping the Huawei previous-days sync window, up to and
  including the current local day, MUST equal the sum of their hourly records over the full local
  period, per plant and per field. Periods outside that window MUST NOT be rewritten. The window
  MUST be derived from the Huawei sync window definition, and "current" MUST be resolved in the
  configured zone.
- **Rationale:** The last aggregation of a month runs at 02:00 on its last day, before that day is
  synced: the sync of previous days stops at yesterday, and today's sync first runs at 06:05. The
  jobs used to refresh only the current period, so no later run touched a closed month and every
  monthly total permanently missed its last local day (23, 24 or 25 hours), and every yearly total
  twelve such days. Huawei data has no publication lag, so the missing day was a permanent shortfall,
  not a delay. The range is the sync's own, derived from the single definition the sync uses, so the
  two cannot drift apart: a period the sync no longer re-reads cannot change, and rewriting it would
  have no effect. A trailing 13-month range was rejected for that reason: it heals nothing, because if
  the sync fails for longer than its window the hourly records are missing and only a manual backfill
  recovers them. The current day is resolved in the configured zone: in the JVM default zone, 00:30 on
  1 October in Madrid was still 30 September. Only plants with an enabled Huawei configuration are
  re-aggregated, because only they are synced.
- **Source:** #384

**Scenarios**

```gherkin
Given October 2026 was last aggregated at 02:00 on 31 October, before that day was synced
When the daily aggregation runs at 02:00 on 2026-11-01 in Europe/Madrid
Then the October 2026 total equals the sum of every hourly record of the local month, including all 24 hours of 31 October and all 25 hours of 25 October

Given the daily aggregation runs on 2026-10-03 in Europe/Madrid
When the periods to refresh are resolved
Then September and October 2026 are rewritten, and August 2026 and the year 2025 are not

Given the daily aggregation runs at 2026-09-30T22:30Z for a plant in Europe/Madrid
When the current day is resolved
Then it is 2026-10-01, and October 2026 is the current month
```

## Impossible states

- Two pre-aggregate points for the same plant and period — prevented by the point key: measurement,
  the plant's station code as the only tag, and the local start of the period as the timestamp, so
  re-aggregating replaces the point.
