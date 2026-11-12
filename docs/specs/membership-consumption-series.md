# Spec — Membership consumption series

| | |
| --- | --- |
| **Prefix** | `MCS` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Memberships`; operations `getMembershipMonthlyConsumption`, `getMembershipDailyConsumption`, `getMembershipHourlyConsumption` |
| **Related ADRs** | — |
| **Last reviewed against code** | #569 |

## Purpose

The consumption series of a membership: the energy and the estimated savings of every supply a
member owns in a community, added up per bucket on the server. It owns how the buckets are laid out
and what they report when only some supplies, or none, stored data. The period a series is computed
over follows EPR-001 to EPR-004 (`energy-periods.md`), the hourly series' with the length limit of
EPR-005 in place of EPR-004; the precision and names of its energy figures
follow `energy-values.md`. Whether a supply has data in a bucket follows the publication threshold
of ENM-001 (`membership-energy-metrics.md`). The rest of the capability is specified lazily, as
issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Membership's supplies | The supplies the member currently owns in the community, the same set the membership energy metrics read. |
| Published record | An hourly record that carries self-consumption, zero included. Datadis publishes a month's self-consumption around the 10th of the following month; until then the month's records carry consumption and surplus but no self-consumption. |
| Supply with data (in a month) | A supply whose published records of that local month, inside the period, number at least 90 % of the month's hours inside the period (the threshold of ENM-001). |
| Supply with data (on a day) | A supply whose published records of that local day, inside the period, number at least 90 % of the day's hours inside the period (the threshold of ENM-001). |
| Supply with data (in an hour) | A supply whose hourly record of that hour is published. |
| Gap | A month, a day or an hour in which no supply of the membership has data. |
| Measured zero | A month, a day or an hour in which at least one supply has data, and its records carry no energy. |

## Rules

### MCS-001 — The daily series emits every local day, and a gap has no savings

- **Status:** Active
- **Rule:** The membership daily series MUST emit one bucket for every local day of the community's
  time zone that overlaps the period, as clipped under EPR-002, in chronological order, whether or
  not any supply has data that day. A day the period starts or ends inside MUST count only the part
  of the day inside the period. It MUST report `savingsEur` and `tariffSource` null when no supply
  has data that day, and only then; a measured zero MUST report savings of zero. A bucket counts a
  supply as with data only when its records carry published self-consumption: at the publication
  threshold for daily and monthly buckets, and by presence for hourly ones. The gap shape is
  unchanged.
- **Rationale:** A chart needs a regular time axis, so a day is never omitted. Gaps and measured
  zeros are different statements: one says the distributor has not published anything, the other
  that nothing was saved. The per-supply daily series tells them apart only through
  `obtainMethod`, so leaving the membership series to the client would lose the distinction. Days
  are the community's because the membership is read in the community's time zone, the same as its
  period. Before publication a record carries no self-consumption, and must not be read as 0.
- **Source:** #410, #569

**Scenarios**

```gherkin
Given a membership with two supplies, neither of which stored a record on 10 January
When its daily series is read from 10 to 12 January
Then 10 January is emitted with null savings and a null tariff source
And 11 January is emitted

Given a membership whose two supplies stored only records carrying zeros on 11 January
When its daily series is read for that day
Then the day's savings are 0.00

Given a membership whose two supplies stored every hour of 10 May with consumption but no self-consumption
When its daily series is read for that day
Then 10 May reports zero energy, null savings and a null tariff source, and no supply with data
```

### MCS-002 — A daily bucket is the server-side sum of the supplies with data, with their count

- **Status:** Active
- **Rule:** Each bucket of the membership daily series MUST be the sum, computed on the server, of
  the figures of the membership's supplies with data that day, savings included, and MUST report
  how many supplies the membership has and how many of them have data that day. The daily bucket
  sums only the supplies with data under the criterion above: a supply's published records of the
  day reach 90 % of the day's hours inside the period.
- **Rationale:** Aggregates are computed on the server, never in the client. The two counts make a
  partly reported day visible: a day where fewer supplies stored data than the membership has is
  incomplete, which its totals alone cannot show. Before publication a record carries no
  self-consumption, and must not be read as 0.
- **Source:** #410, #569

**Scenarios**

```gherkin
Given a membership with two supplies, only one of which stored records on 10 January
When its daily series is read for that day
Then the day reports two supplies, one of them with data
And its energy and savings are that supply's

Given a membership with two supplies whose records carry self-consumption at 9 and at 8 of the 10 hours from 00:00 to 10:00 on 10 June
When its daily series is read over those 10 hours
Then the day reports two supplies, one of them with data
```

### MCS-003 — The hourly series emits every hour by its start instant, sums the supplies with data, and a gap has no savings

- **Status:** Active
- **Rule:** The membership hourly series MUST emit every whole hour starting inside the clipped
  period, keyed by `startsAt`, under the same rule as the supply hourly series (SCS-001), whether or
  not any supply stored a record that hour. Each bucket MUST be the server-side sum of the figures of
  the member's supplies with data that hour, with the supply counts, as MCS-002 states for a day. It
  MUST report `savingsEur` and `tariffSource` null when no supply has data that hour, and only
  then; a measured zero MUST report savings of zero, as MCS-001 states for a day. The hourly bucket
  sums only the supplies whose record carries self-consumption.
- **Rationale:** As MCS-001 and MCS-002, at hourly resolution: a chart of one day needs every hour,
  and a gap and a measured zero are different statements. An hour is matched across supplies by the
  instant it starts at, never by its local date and time, which two hours share on the day the
  clocks go back; summing by label would merge them. Before publication a record carries no
  self-consumption, and must not be read as 0.
- **Source:** #546, #569

**Scenarios**

```gherkin
Given a membership with two supplies, only one of which stored a record at 13:00 on 10 January
When its hourly series is read for that day
Then 24 hours are emitted
And 13:00 reports two supplies, one of them with data, and that supply's energy and savings
And an hour in which neither supply stored a record has null savings and a null tariff source

Given a membership whose second supply's record at 12:00 on 10 June carries consumption but no self-consumption
When its hourly series is read for that day
Then 12:00 counts only the first supply as with data, and sums only its figures

Given a membership whose two supplies stored records at both 02:00 hours of 27 October 2024
When its hourly series is read for that day
Then 25 hours are emitted
And the two 02:00 hours have the start instants 02:00+02:00 and 02:00+01:00, each with its own figures
```

### MCS-004 — The monthly series builds each month from hourly records, counting a supply only at the publication threshold

- **Status:** Active
- **Rule:** The membership monthly series MUST build each bucket from hourly records, with the
  publication threshold per supply per month. It MUST emit one bucket for every month whose local
  midnight on day 1, in the community's time zone, falls inside the period as clipped under EPR-002,
  in chronological order, whether or not any supply has data that month. Each bucket MUST be the
  server-side sum of the figures of the membership's supplies with data that month, counting only
  each supply's records inside the period and pricing their savings over the same hours, with the
  supply counts, as MCS-002 states for a day; a gap and a measured zero follow MCS-001.
- **Rationale:** The monthly pre-aggregates store missing self-consumption as 0. They also keep no
  count of the records carrying it, so they cannot tell a published month from one aggregated
  before publication; a supply published later than another was summed as zero and the month was
  not marked partial, while the daily view of the same month was. Building the month from the
  hourly records, with the threshold the community monthly series applies (#554), makes every
  membership series count the same supplies with data. A month the period ends inside is summed and
  priced over its hours inside the period, as a day is, rather than as a whole calendar month.
- **Source:** #569

**Scenarios**

```gherkin
Given a membership with two supplies, the first published for June 2024 and the second with records in June that carry no self-consumption
When its monthly series is read for June
Then June reports two supplies, one of them with data, and the first supply's energy and savings

Given a supply whose records carry self-consumption at 648 of the 720 hours of June 2024, and another at 647
When the monthly series of their membership is read for June
Then June counts the first supply as with data and not the second

Given a stale monthly pre-aggregate for June and hourly records that differ from it
When the monthly series is read for June
Then June reports the sums of the hourly records
```

## Impossible states

- A daily series without any day — prevented by EPR-003: the start is before the start of the
  current hour, so the clipped period always overlaps at least one day.
- Two hours of the hourly series sharing a `startsAt` — prevented by MCS-003: the hours are counted
  on the instant line, one hour apart.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Disabled supplies are part of the membership's supplies: they count in the number of supplies and are summed when they have data. | The daily, hourly and monthly series reuse the supply set of the membership energy metrics, pending a decision on what a disabled supply means for a member's figures. | That decision is taken and applied to every membership series. | #410, #546, #569 |
| Each supply's day is bucketed in the supply's time zone and folded into the community's day by date. | Both zones resolve to the application zone today, so the dates agree. | Supply or community time zones become configurable separately. | #410 |
| A period of the hourly series that holds no whole hour start (for example `[10:30, 10:45)`) returns no bucket. | EPR-001 only requires the start to be before the end, and every hour that starts inside such a period is emitted: there is none, as in the supply hourly series. | A minimum length or hour-aligned bounds are required. | #546 |
