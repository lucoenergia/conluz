# Spec — Datadis consumption pre-aggregation

| | |
| --- | --- |
| **Prefix** | `DCA` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → read by `getMembershipMonthlyConsumption`, among others; rebuilt on demand by `syncMonthlyDatadisConsumptions`, `syncYearlyDatadisConsumptions`, `syncMonthlyDatadisProduction`, `syncYearlyDatadisProduction` |
| **Related ADRs** | — |
| **Last reviewed against code** | #380 |

## Purpose

The monthly and yearly totals the scheduled jobs pre-compute, per supply, from the hourly records
the daily Datadis sync stores, so that monthly and yearly series need not sum the hourly records on
every request. Both series come from Datadis consumption data: the production pre-aggregates sum the
hourly production the sync derives from the surplus of plant supplies. This spec covers only how
fresh those totals are kept; the rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Hourly re-sync window | The period the daily sync re-reads from Datadis on every run: from the first day of the month one year before the run's date through that date. A month belongs to the window when any of its days does, so the window holds 13 months, the month it starts in included. The aggregation jobs take the run's date in the community's time zone. |
| Monthly pre-aggregate | The stored total of one supply for one calendar month, one value per energy quantity, summed over the month as the community's calendar sees it (from local midnight of the 1st to local midnight of the 1st of the next month). |
| Yearly pre-aggregate | The stored total of one supply for one calendar year, summed from its monthly pre-aggregates over the year as the community's calendar sees it. |
| Daily aggregation run | One run of the scheduled monthly jobs (05:00, consumption and production) or of the scheduled yearly jobs (06:00, consumption and production), over every community with Datadis enabled. |

## Rules

### DCA-001 — The monthly consumption pre-aggregate is current for every month of the re-sync window

- **Status:** Active
- **Rule:** After each daily aggregation run, the monthly consumption pre-aggregate for every month
  inside the hourly re-sync window MUST equal the sum of that month's hourly consumption records,
  per supply and per field. Months outside the window MUST NOT be rewritten.
- **Rationale:** Datadis publishes a month's self-consumption around the 10th of the following month
  and revises its consumption afterwards. The daily sync writes those revisions to the hourly
  records, but the monthly job used to re-aggregate only the current month, so every closed month
  stayed at whatever preliminary data it held at 05:00 on its last day. In #375, 31 of 31 supplies
  had at least one closed month whose stored total disagreed with its hourly records, most with zero
  self-consumption, and the member home showed no energy in the twelve-month chart where the
  previous-month comparison, summed from the hourly records, showed real energy and savings. The
  window is the sync's own, derived from the single definition the sync uses, so the two cannot
  drift apart: a month the sync no longer re-reads cannot change, and rewriting it would only add
  load. The current month is resolved in the community's zone: in the JVM default zone, 00:30 on
  1 October in Madrid was still September and the window ended a month early. Only communities
  with Datadis enabled are re-aggregated, because only they are synced; the hourly records of the
  others do not change.
- **Source:** #375, #380

**Scenarios**

```gherkin
Given May 2026 was aggregated with no self-consumption and its hourly records now carry the published self-consumption
When the daily aggregation runs on 2026-10-09
Then the May 2026 total of every field equals the sum of May's hourly records

Given the daily aggregation runs at 2026-09-30T22:30Z for a community in Europe/Madrid
When the current month is resolved
Then it is October 2026, and the window is October 2025 to October 2026
```

### DCA-002 — The monthly production pre-aggregate is current for every month of the re-sync window

- **Status:** Active
- **Rule:** After each daily aggregation run, the monthly production pre-aggregate for every month
  inside the hourly re-sync window MUST equal the sum of that month's hourly production records,
  per supply and per field. Months outside the window MUST NOT be rewritten.
- **Rationale:** The daily sync rewrites the hourly production of plant supplies over the same
  window as their consumption, so the production totals froze in the same way. The month is summed
  between local midnights, as in DCA-001: the production aggregation used literal UTC bounds, which in
  Madrid dropped the first local hour or two of every month and counted the first of the next month
  instead (#380), so re-aggregating the window alone would only have rewritten the wrong sums.
- **Source:** #375, #380

### DCA-003 — The yearly pre-aggregates are current for every year the re-sync window overlaps

- **Status:** Active
- **Rule:** After each daily yearly aggregation run, the yearly consumption and production
  pre-aggregates for every year overlapping the hourly re-sync window MUST equal the sum of that
  year's monthly pre-aggregate points, per supply and per field. Years outside the window MUST NOT
  be rewritten.
- **Rationale:** The yearly totals are summed from the monthly ones. Once DCA-001 and DCA-002 rewrite
  every month of the window, the window reaches into the previous year (October to December 2025 on
  2026-10-09), and a yearly job that only re-aggregated the current year left that year's total at
  the monthly values it summed before they were corrected. The year is summed between local
  midnights of 1 January: monthly points are stamped at local midnight of the 1st, so January sits at
  23:00Z of the previous year, and the production yearly aggregation, which used literal UTC bounds,
  counted every January in the previous year's total and none in its own (#380).
- **Source:** #375, #380

## Impossible states

- Two pre-aggregate points for the same supply and period — prevented by the point key: measurement,
  the supply's code as the only tag, and the local start of the period as the timestamp, so a rewrite
  replaces the point.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A monthly total can lag its hourly records by one day when the 04:00 sync runs past 05:00: the monthly jobs then read partly re-synced records. | The jobs are scheduled by time, not chained; the next daily run reads complete records and corrects the total. | The monthly re-aggregation is triggered by completion of the sync, or a sync run is observed to exceed the gap between schedules. | #380 |
| A yearly total can lag the monthly totals by one day when the 05:00 monthly jobs run past 06:00: the yearly jobs then read partly rewritten monthly points. | The jobs are scheduled by time, not chained; the next daily run reads complete monthly points and corrects the total. | The yearly re-aggregation is triggered by completion of the monthly run, or a monthly run is observed to exceed the gap between schedules. | #380 |
