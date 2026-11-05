# Spec — Supply consumption series

| | |
| --- | --- |
| **Prefix** | `SCS` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operation `getSupplyHourlyConsumption` |
| **Related ADRs** | — |
| **Last reviewed against code** | #545 |

## Purpose

The consumption series of a single supply: its energy and estimated savings, bucket by bucket. It
owns how the buckets are laid out, what a bucket with no stored record reports, and what each
bucket is priced over. The period a series is computed over follows `energy-periods.md`; the
precision and names of its energy figures follow `energy-values.md`. Only the hourly series is
specified so far; the daily, monthly and yearly series are specified lazily, as issues touch them.

## Glossary

| Term | Meaning |
| --- | --- |
| Hour of the period | An hour whose start instant lies inside the period, as clipped under EPR-002. |
| `startsAt` | The instant an hour starts at, with the offset in force in the supply's time zone at that instant. |
| Gap | An hour for which the supply stored no record. |
| Measured zero | An hour for which the supply stored a record carrying no energy. |

## Rules

### SCS-001 — The hourly series emits every hour of the period with its start instant, each priced over itself

- **Status:** Active
- **Rule:** The supply hourly series MUST read its period under EPR-001 to EPR-003, with the length
  limit of EPR-005. It MUST emit one bucket for every hour of the period, in chronological order,
  whether or not the supply stored a record for it, each with its `startsAt`. A gap MUST be
  emitted with zero energy, a null `obtainMethod` and savings of zero; a measured zero MUST keep its
  `obtainMethod`. Each hour MUST be priced with the same tariff resolution as a day of the daily
  series, over the hour clipped to the end of the period, and rounded as the daily series rounds.
- **Rationale:** The historical analysis needs an hourly view of a period, savings per hour, and a
  gap told apart from a measured zero; before #545 the series read both bounds inclusively, carried
  no savings and had no cap. A chart needs a regular time axis, so an hour is never omitted, and
  the axis is built by the series itself because the store returns nothing at all for a period in
  which the supply has no record. The local `date` and `time` repeat on the day the clocks go back,
  so `startsAt` is the only key that identifies every hour of that day. An hour that started before
  the period is not emitted: records are stamped at the start of their hour (#322), so its record is
  outside the period and the hour would only show a zero that was never measured.
- **Source:** #545

**Scenarios**

```gherkin
Given a supply with records at 00:00 and 12:00 on 1 September 2026 and at 00:00 on 2 September
When its hourly series is read from 2026-09-01T00:00:00+02:00 to 2026-09-02T00:00:00+02:00
Then 24 buckets are returned, starting at 2026-09-01T00:00:00+02:00 and ending at 2026-09-01T23:00:00+02:00
And the record at 00:00 on 2 September is not included

Given a supply with a record for each of the two hours the clocks repeat on 25 October 2026
When its hourly series is read for that day
Then 25 buckets are returned
And two of them have the local time 02:00, one starting at 2026-10-25T02:00:00+02:00 and the other at 2026-10-25T02:00:00+01:00

Given a supply that stored no record at all on 1 September 2026
When its hourly series is read for that day
Then 24 buckets are returned, each with zero energy, a null obtain method and savings of 0.00
```

## Impossible states

- An hour emitted twice, or two hours sharing a `startsAt` — prevented by SCS-001: the hours are
  counted on the instant line, one per start instant.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A period that holds no whole hour start (for example `[10:30, 10:45)`) returns no bucket. | EPR-001 only requires the start to be before the end, and every hour that starts inside such a period is emitted: there is none. | A minimum length or hour-aligned bounds are required. | #545 |
| Hours are whole hours of UTC, the way the store groups them. | Every offset of the application's time zone is a whole number of hours, so they are also whole local hours. | A supply's time zone can have an offset that is not a whole number of hours. | #545 |
