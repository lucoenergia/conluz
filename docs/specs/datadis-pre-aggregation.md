# Spec — Datadis pre-aggregation

| | |
| --- | --- |
| **Prefix** | `DCA` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → read by `getSupplyYearlyConsumption`, among others, but no longer by the monthly consumption series (#569); rebuilt on demand by `syncMonthlyDatadisConsumptions`, `syncYearlyDatadisConsumptions`, `syncMonthlyDatadisProduction`, `syncYearlyDatadisProduction` |
| **Related ADRs** | — |
| **Last reviewed against code** | #630 |

## Purpose

The monthly and yearly totals the scheduled jobs pre-compute, per supply, from the hourly records
the daily Datadis sync stores, so that monthly and yearly series need not sum the hourly records on
every request. The membership and supply monthly consumption series do sum the hourly records when
they are read, since #569. The spec covers both pre-aggregated series, consumption and production.
Both come from Datadis consumption data: the production pre-aggregates sum the hourly production the
sync derives from the surplus of plant supplies. This spec covers only how fresh those totals are kept;
the rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Full re-sync window | The widest period the Datadis sync re-reads: from the first day of the month 24 months before the run's date through that date, as defined in [`datadis-hourly-ingestion.md`](datadis-hourly-ingestion.md) (DHI-007). The scheduled sync re-reads it on Sundays, and a manual sync re-reads at most it (DHI-008). A month belongs to the window when any of its days does, so the window holds 25 months, the month it starts in included. The aggregation jobs take the run's date in the community's time zone. |
| Monthly pre-aggregate | The stored total of one supply for one calendar month, one value per energy quantity, summed over the month as the community's calendar sees it (from local midnight of the 1st to local midnight of the 1st of the next month). |
| Yearly pre-aggregate | The stored total of one supply for one calendar year, summed from its monthly pre-aggregates over the year as the community's calendar sees it. |
| Daily aggregation run | One run of the scheduled monthly jobs (05:00, consumption and production) or of the scheduled yearly jobs (06:00, consumption and production), over every community with Datadis enabled. |

## Rules

### DCA-001 — The monthly consumption pre-aggregate is current for every month of the full re-sync window

- **Status:** Active
- **Rule:** After each daily aggregation run, the monthly consumption pre-aggregate for every month
  inside the full re-sync window MUST equal the sum of that month's hourly consumption records,
  per supply and per field, whichever window the sync re-read that day. Months outside it MUST NOT
  be rewritten.
- **Rationale:** Datadis publishes a month's self-consumption around the 10th of the following month
  and revises its consumption afterwards. The daily sync writes those revisions to the hourly
  records, but the monthly job used to re-aggregate only the current month, so every closed month
  stayed at whatever preliminary data it held at 05:00 on its last day. In #375, 31 of 31 supplies
  had at least one closed month whose stored total disagreed with its hourly records, most with zero
  self-consumption, and the member home showed no energy in the twelve-month chart where the
  previous-month comparison, summed from the hourly records, showed real energy and savings. The
  window is the sync's full window, derived from the single definition the sync uses, so the two
  cannot drift apart: a month no sync re-reads cannot change, and rewriting it would only add load.
  Since #630 the scheduled sync re-reads only 3 months back on most days and 24 on Sundays, and a
  manual sync can re-read up to 24 on any day. The aggregation covers the full window every day,
  whichever window the sync used: it is local and cheap, and a revision brought in by the Sunday sync
  or by a manual sync is then aggregated within 24 hours, without depending on how long the jobs
  take. The current month is resolved in the community's zone: in the JVM default zone, 00:30 on
  1 October in Madrid was still September and the window ended a month early. Only communities
  with Datadis enabled are re-aggregated, because only they are synced; the hourly records of the
  others do not change.
- **Source:** #375, #380, #630

**Scenarios**

```gherkin
Given May 2026 was aggregated with no self-consumption and its hourly records now carry the published self-consumption
When the daily aggregation runs on 2026-10-09
Then the May 2026 total of every field equals the sum of May's hourly records

Given the daily aggregation runs at 2026-09-30T22:30Z for a community in Europe/Madrid
When the current month is resolved
Then it is October 2026, and the window is October 2024 to October 2026

Given Thursday 2026-11-26, a day the sync re-reads only the daily window
When the daily aggregation runs for a community in Europe/Madrid
Then every month from November 2024 to November 2026 is re-aggregated, and October 2024 is not rewritten
```

### DCA-002 — The monthly production pre-aggregate is current for every month of the full re-sync window

- **Status:** Active
- **Rule:** After each daily aggregation run, the monthly production pre-aggregate for every month
  inside the full re-sync window MUST equal the sum of that month's hourly production records,
  per plant supply (the scope of DCA-004) and per field, whichever window the sync re-read that day.
  Months outside it MUST NOT be rewritten.
- **Rationale:** The sync rewrites the hourly production of plant supplies over the same
  window as their consumption, so the production totals froze in the same way. They cover the full
  window every day for the same reasons as DCA-001. The month is summed between local midnights, as in DCA-001: the production aggregation used literal UTC bounds, which in
  Madrid dropped the first local hour or two of every month and counted the first of the next month
  instead (#380), so re-aggregating the window alone would only have rewritten the wrong sums.
  The pre-aggregate is kept per plant supply because only plant supplies have hourly production
  (DCA-004).
- **Source:** #375, #380, #524, #630

### DCA-003 — The yearly pre-aggregates are current for every year the full re-sync window overlaps

- **Status:** Active
- **Rule:** After each daily yearly aggregation run, the yearly consumption and production
  pre-aggregates for every year overlapping the full re-sync window MUST equal the sum of that
  year's monthly pre-aggregate points, per field: the consumption pre-aggregates per supply, the
  production pre-aggregates per plant supply (the scope of DCA-004), whichever window the sync
  re-read that day. Years outside the window MUST NOT be rewritten.
- **Rationale:** The yearly totals are summed from the monthly ones. Once DCA-001 and DCA-002 rewrite
  every month of the window, the window reaches into previous years (November 2024 to December 2025
  on 2026-11-26), and a yearly job that only re-aggregated the current year left their totals at
  the monthly values it summed before they were corrected. It covers the full window every day for
  the same reasons as DCA-001. The year is summed between local
  midnights of 1 January: monthly points are stamped at local midnight of the 1st, so January sits at
  23:00Z of the previous year, and the production yearly aggregation, which used literal UTC bounds,
  counted every January in the previous year's total and none in its own (#380). The production
  totals follow the scope of DCA-004 because they are summed from the monthly production
  pre-aggregates, which exist only for plant supplies.
- **Source:** #375, #380, #524, #630

### DCA-004 — The production pre-aggregates are computed only for plant supplies

- **Status:** Active
- **Rule:** The monthly and yearly Datadis production pre-aggregates MUST be computed only for
  supplies linked to a plant of the community, by the scheduled jobs and by
  `syncMonthlyDatadisProduction` and `syncYearlyDatadisProduction` alike. A `supplyCode` of a
  community supply that is not linked to a plant MUST be answered as not found.
- **Rationale:** Only plant supplies have hourly Datadis production (the sync derives it from their
  surplus) and only their production is read. Aggregating every other supply ran queries that
  could never find data and filled the logs with WARN lines that hid a plant supply with no
  production. A `supplyCode` that is not a plant supply is answered as not found, as the production
  read path answers a supply that does not back a plant of the community.
- **Source:** #524

**Scenarios**

```gherkin
Given a Datadis-enabled community with two plant supplies and two supplies not linked to a plant, all with hourly production
When the daily production aggregation runs
Then the two plant supplies get their monthly and yearly production totals
And the other two supplies get no production point, and keep any they already had

Given a supply of the community that is not linked to a plant
When a community admin asks syncMonthlyDatadisProduction or syncYearlyDatadisProduction to aggregate it
Then the request is answered as not found
```

## Impossible states

- Two pre-aggregate points for the same supply and period — prevented by the point key: measurement,
  the supply's code as the only tag, and the local start of the period as the timestamp, so a rewrite
  replaces the point.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A monthly total can lag its hourly records by one day when the 04:00 sync runs past 05:00: the monthly jobs then read partly re-synced records. | The jobs are scheduled by time, not chained; the next daily run reads complete records and corrects the total. | The monthly re-aggregation is triggered by completion of the sync, or a sync run is observed to exceed the gap between schedules. | #380 |
| A yearly total can lag the monthly totals by one day when the 05:00 monthly jobs run past 06:00: the yearly jobs then read partly rewritten monthly points. | The jobs are scheduled by time, not chained; the next daily run reads complete monthly points and corrects the total. | The yearly re-aggregation is triggered by completion of the monthly run, or a monthly run is observed to exceed the gap between schedules. | #380 |
