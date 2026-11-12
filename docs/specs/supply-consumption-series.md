# Spec — Supply consumption series

| | |
| --- | --- |
| **Prefix** | `SCS` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operations `getSupplyHourlyConsumption`, `getSupplyDailyConsumption`, `getSupplyMonthlyConsumption` |
| **Related ADRs** | — |
| **Last reviewed against code** | #569 |

## Purpose

The consumption series of a single supply: its energy and estimated savings, bucket by bucket. It
owns how the buckets are laid out, what a bucket with no stored record reports, and what each
bucket is priced over. The period a series is computed over follows `energy-periods.md`; the
precision and names of its energy figures follow `energy-values.md`. Whether a bucket has data
follows the publication threshold of ENM-001 (`membership-energy-metrics.md`). The hourly, daily
and monthly series are specified; the yearly series is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Hour of the period | An hour whose start instant lies inside the period, as clipped under EPR-002. |
| `startsAt` | The instant an hour starts at, with the offset in force in the supply's time zone at that instant. |
| Published record | An hourly record that carries self-consumption, zero included. Datadis publishes a month's self-consumption around the 10th of the following month; until then the month's records carry consumption and surplus but no self-consumption. |
| Hour with data | An hour whose record is published. |
| Day with data, month with data | A local day, or a local month, whose published records inside the period number at least 90 % of its hours inside the period (the threshold of ENM-001). |
| Gap | An hour or a day without data: no record, or records without published self-consumption. |
| Measured zero | An hour or a day with data whose records carry no energy. |

## Rules

### SCS-001 — The hourly series emits every hour of the period with its start instant, each priced over itself

- **Status:** Active
- **Rule:** The supply hourly series MUST read its period under EPR-001 to EPR-003, with the length
  limit of EPR-005. It MUST emit one bucket for every hour of the period, in chronological order,
  whether or not the supply stored a record for it, each with its `startsAt`. A gap MUST be
  emitted with zero energy, a null `obtainMethod` and savings of zero; a measured zero MUST keep its
  `obtainMethod`. An hourly bucket whose record lacks self-consumption is a gap. Each hour MUST be priced with the same tariff resolution as a day of the daily
  series, over the hour clipped to the end of the period, and rounded as the daily series rounds.
- **Rationale:** The historical analysis needs an hourly view of a period, savings per hour, and a
  gap told apart from a measured zero; before #545 the series read both bounds inclusively, carried
  no savings and had no cap. A chart needs a regular time axis, so an hour is never omitted, and
  the axis is built by the series itself because the store returns nothing at all for a period in
  which the supply has no record. The local `date` and `time` repeat on the day the clocks go back,
  so `startsAt` is the only key that identifies every hour of that day. An hour that started before
  the period is not emitted: records are stamped at the start of their hour (#322), so its record is
  outside the period and the hour would only show a zero that was never measured. Before
  publication a record carries no self-consumption, and must not be read as 0. A bucket without published self-consumption is a gap even when consumption is known, so that no false self-consumption or savings is shown; the consumption appears once self-consumption is published.
- **Source:** #545, #569

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

Given a supply whose records of 10 June 2024 carry consumption at every hour but self-consumption only up to 12:00
When its hourly series is read for that day
Then the hours from 12:00 on are returned with zero energy, a null obtain method and savings of 0.00
```

### SCS-002 — The monthly series builds each month from hourly records and omits a month below the threshold

- **Status:** Active
- **Rule:** The supply monthly series MUST build each bucket from hourly records; a month below the
  threshold is omitted. A month MUST be selected when its local midnight on day 1 falls inside the
  period, as clipped under EPR-002, and returned, in chronological order, only when the supply has
  data in it. Each month MUST total the supply's records inside the period and be priced over the
  same hours, with the same tariff resolution as a day of the daily series.
- **Rationale:** Same source, same defect: the monthly pre-aggregates store missing
  self-consumption as 0 and keep no count of the records carrying it, so a month aggregated before
  publication read as a measured zero. A month is omitted rather than returned as a gap because the
  series already omitted a month without records, and the response has no nullable figure to mark
  one. A month the period ends inside is summed and priced over its hours inside the period, as a
  day is, rather than as a whole calendar month.
- **Source:** #569

**Scenarios**

```gherkin
Given a supply whose records carry self-consumption at 648 of the 720 hours of June 2024
When its monthly series is read for June
Then June is returned

Given a supply whose records carry self-consumption at 647 of the 720 hours of June 2024
When its monthly series is read for June
Then no month is returned

Given a supply published for February 2024, with half of its self-consumption before the 15th
When its monthly series is read from 1 February to 12:00 on 15 February
Then February carries only the energy and savings of its hours before 12:00 on the 15th
```

### SCS-003 — The daily series treats a day below the publication threshold as a gap

- **Status:** Active
- **Rule:** The supply daily series MUST treat a day below the publication threshold as a gap. A day
  has data when its published records inside the period number at least 90 % of the day's hours
  inside the period; a day without data MUST be returned with zero energy, a null `obtainMethod` and
  savings of zero, as SCS-001 states for an hour, and a measured zero MUST keep its `obtainMethod`.
- **Rationale:** A day before publication is not a measured 0. A bucket without published self-consumption is a gap even when consumption is known, so that no false self-consumption or savings is shown; the consumption appears once self-consumption is published.
- **Source:** #569

**Scenarios**

```gherkin
Given a supply whose records carry self-consumption at 9 of the 10 hours from 00:00 to 10:00 on 10 June 2024
When its daily series is read over those 10 hours
Then 10 June is returned with its energy and its obtain method

Given a supply whose records carry consumption at every hour from 00:00 to 10:00 on 10 June 2024 and self-consumption at 8 of them
When its daily series is read over those 10 hours
Then 10 June is returned with zero energy, a null obtain method and savings of 0.00
```

## Impossible states

- An hour emitted twice, or two hours sharing a `startsAt` — prevented by SCS-001: the hours are
  counted on the instant line, one per start instant.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A period that holds no whole hour start (for example `[10:30, 10:45)`) returns no bucket. | EPR-001 only requires the start to be before the end, and every hour that starts inside such a period is emitted: there is none. | A minimum length or hour-aligned bounds are required. | #545 |
| Hours are whole hours of UTC, the way the store groups them. | Every offset of the application's time zone is a whole number of hours, so they are also whole local hours. | A supply's time zone can have an offset that is not a whole number of hours. | #545 |
