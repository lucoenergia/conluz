# Spec — Energy periods

| | |
| --- | --- |
| **Prefix** | `EPR` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tags `Supplies`, `Memberships`; operations `getSupplyEnergyMetrics`, `getSupplyDailyConsumption`, `getSupplyMonthlyConsumption`, `getMembershipEnergyMetrics`, `getMembershipMonthlyConsumption`, `getMembershipDailyConsumption`, `getMembershipHourlyProfile` |
| **Related ADRs** | — |
| **Last reviewed against code** | #410 |

## Purpose

The period a request for energy metrics or a consumption series is computed over: how its bounds
are read, which requested periods are rejected, and how a period reaching into the current hour is
cut short. It owns these conventions for the operations listed above; the supply hourly and yearly
series, the production endpoints, payback and the CSV report are not covered.

## Glossary

| Term | Meaning |
| --- | --- |
| Period | The interval `[startDate, endDate)` a response is computed over, either requested by the caller or resolved by the server (the latest published month, or the span of the stored records). |
| Period's time zone | The supply's time zone for a supply operation, the community's for a membership operation. |
| Start of the current hour | The current instant, read in the period's time zone and truncated to the hour. |

## Rules

### EPR-001 — Periods are half-open, and the start is strictly before the end

- **Status:** Active
- **Rule:** On the operations listed above, a period MUST be half-open, `[startDate, endDate)`: a
  record stamped at `startDate` belongs to it and one stamped at `endDate` does not. A day MUST run
  from local midnight to the following local midnight, and a month from local midnight of its first
  day to local midnight of the first day of the next month, each bound with the offset in force at
  that instant. A monthly series MUST select a month when its local midnight on day 1 falls inside
  the period. Every period the server resolves MUST be half-open too: the latest published month as
  above, and the span of the stored records from the first record to the end of the hour of the
  last. A requested `startDate` that is not strictly before `endDate` MUST be rejected.
- **Rationale:** Before #409 the same period meant different records on different operations: the
  daily series documented `00:00`–`23:59:59`, energy metrics `00:00`–`23:00`, and both bounds were
  inclusive, so a period ending at the next midnight counted the boundary hour twice. A half-open
  period holds the same records on every operation whatever the data resolution, and adjacent periods
  never share a record. It matches the domain's other validity intervals (a coefficient's `validTo`,
  the effective date of a supply's leave) and the production endpoints, which already cut half-open.
  Equal bounds make an empty period, so they are rejected like inverted ones.
- **Source:** #409

**Scenarios**

```gherkin
Given records at 2026-08-10T00:00+02:00 and at 2026-08-11T00:00+02:00
When energy metrics are requested for [2026-08-10T00:00+02:00, 2026-08-11T00:00+02:00)
Then the first record is counted, the second is not, and the period spans 24 hours

Given the latest published month is August 2026
When energy metrics are requested with period=LATEST_PUBLISHED_MONTH
Then the period is [2026-08-01T00:00+02:00, 2026-09-01T00:00+02:00) and spans 744 hours
```

### EPR-002 — An end after the start of the current hour is clipped to it

- **Status:** Active
- **Rule:** A requested `endDate` after the start of the current hour MUST be replaced by the start of
  the current hour before any data is read and before coverage is computed. Energy metrics MUST
  report the clipped end in their period. A series MUST NOT emit a bucket starting at or after the
  clipped end.
- **Rationale:** Expected hours used to count hours that had not happened yet, so coverage dropped for
  any period ending in the future. The client must see the period that was actually computed, so the
  clipped bound is returned rather than the one requested. The start of the hour, not the current
  instant, is the cut because the current hour has not fully happened and its record, if any, is not
  final.
- **Source:** #409

**Scenarios**

```gherkin
Given the clock reads 2026-10-26T10:30 in Europe/Madrid
And a record at 2026-10-26T10:00+01:00
When energy metrics are requested from 2026-10-26T00:00+01:00 to 2026-11-01T00:00+01:00
Then the period ends at 2026-10-26T10:00+01:00, spans 10 hours and does not count the 10:00 record
```

### EPR-003 — A start at or after the start of the current hour is rejected

- **Status:** Active
- **Rule:** A requested `startDate` at or after the start of the current hour MUST be rejected.
- **Rationale:** Such a period has not begun, and clipping it under EPR-002 would leave it empty; an
  entirely future period is a client error, not an empty answer.
- **Source:** #409

### EPR-004 — A requested period is at most 24 calendar months long

- **Status:** Active
- **Rule:** A requested period whose `endDate` is later than `startDate` plus 24 calendar months,
  counted in the period's time zone, MUST be rejected. The limit MUST be evaluated on the requested
  bounds, before EPR-002 clips the end.
- **Rationale:** A cost guard: every request reads the hourly measurement or its pre-aggregates over
  the whole period. Months before the Datadis sync window (by default, the current month and the 12
  before it) are no longer re-aggregated, so a longer range adds cost without adding fresher data. Evaluating it before clipping keeps the
  answer to a request independent of the time it is sent.
- **Source:** #409

## Impossible states

- A requested period whose end is before its start reaching the data — prevented by EPR-001, checked
  before anything is read.
- A clipped period that is empty — prevented by EPR-003: the start is before the start of the current
  hour, so the clipped end is always after it.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| The supply hourly series keeps both bounds inclusive. | Out of the scope of #409, which left that endpoint and its CSV report unchanged. | The supply hourly series is aligned with EPR-001. | #409 |
| A period resolved from the span of the stored records is neither capped nor clipped. | It is not a requested range, so EPR-003 and EPR-004 do not apply to it. | The recorded-period fallback is capped or removed. | #409 |
