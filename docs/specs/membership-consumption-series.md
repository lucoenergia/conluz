# Spec — Membership consumption series

| | |
| --- | --- |
| **Prefix** | `MCS` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Memberships`; operations `getMembershipDailyConsumption`, `getMembershipHourlyConsumption` |
| **Related ADRs** | — |
| **Last reviewed against code** | #546 |

## Purpose

The consumption series of a membership: the energy and the estimated savings of every supply a
member owns in a community, added up per bucket on the server. It owns how the buckets are laid out
and what they report when only some supplies, or none, stored data. The period a series is computed
over follows EPR-001 to EPR-004 (`energy-periods.md`), the hourly series' with the length limit of
EPR-005 in place of EPR-004; the precision and names of its energy figures
follow `energy-values.md`. The rest of the capability, the membership monthly series included, is
specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Membership's supplies | The supplies the member currently owns in the community, the same set the membership energy metrics and the membership monthly series read. |
| Supply with data (on a day) | A supply that stored at least one hourly record that local day, inside the period. A record carrying zero energy counts. |
| Supply with data (in an hour) | A supply that stored the hourly record of that hour. A record carrying zero energy counts. |
| Gap | A day, or an hour, in which no supply of the membership stored a record. |
| Measured zero | A day, or an hour, in which at least one supply stored records, and they carry no energy. |

## Rules

### MCS-001 — The daily series emits every local day, and a gap has no savings

- **Status:** Active
- **Rule:** The membership daily series MUST emit one bucket for every local day of the community's
  time zone that overlaps the period, as clipped under EPR-002, in chronological order, whether or
  not any supply stored a record that day. A day the period starts or ends inside MUST count only
  the part of the day inside the period. It MUST report `savingsEur` and `tariffSource` null when no
  supply stored a record that day, and only then; a measured zero MUST report savings of zero.
- **Rationale:** A chart needs a regular time axis, so a day is never omitted. Gaps and measured
  zeros are different statements: one says the distributor has not published anything, the other
  that nothing was saved. The per-supply daily series tells them apart only through
  `obtainMethod`, so leaving the membership series to the client would lose the distinction. Days
  are the community's because the membership is read in the community's time zone, the same as its
  period.
- **Source:** #410

**Scenarios**

```gherkin
Given a membership with two supplies, neither of which stored a record on 10 January
When its daily series is read from 10 to 12 January
Then 10 January is emitted with null savings and a null tariff source
And 11 January is emitted

Given a membership whose two supplies stored only records carrying zeros on 11 January
When its daily series is read for that day
Then the day's savings are 0.00
```

### MCS-002 — A daily bucket is the server-side sum of the supplies with data, with their count

- **Status:** Active
- **Rule:** Each bucket of the membership daily series MUST be the sum, computed on the server, of
  the figures of the membership's supplies with data that day, savings included, and MUST report
  how many supplies the membership has and how many of them have data that day.
- **Rationale:** Aggregates are computed on the server, never in the client. The two counts make a
  partly reported day visible: a day where fewer supplies stored data than the membership has is
  incomplete, which its totals alone cannot show.
- **Source:** #410

**Scenarios**

```gherkin
Given a membership with two supplies, only one of which stored records on 10 January
When its daily series is read for that day
Then the day reports two supplies, one of them with data
And its energy and savings are that supply's
```

### MCS-003 — The hourly series emits every hour by its start instant, sums the supplies with data, and a gap has no savings

- **Status:** Active
- **Rule:** The membership hourly series MUST emit every whole hour starting inside the clipped
  period, keyed by `startsAt`, under the same rule as the supply hourly series (SCS-001), whether or
  not any supply stored a record that hour. Each bucket MUST be the server-side sum of the figures of
  the member's supplies with data that hour, with the supply counts, as MCS-002 states for a day. It
  MUST report `savingsEur` and `tariffSource` null when no supply stored a record that hour, and only
  then; a measured zero MUST report savings of zero, as MCS-001 states for a day.
- **Rationale:** As MCS-001 and MCS-002, at hourly resolution: a chart of one day needs every hour,
  and a gap and a measured zero are different statements. An hour is matched across supplies by the
  instant it starts at, never by its local date and time, which two hours share on the day the
  clocks go back; summing by label would merge them.
- **Source:** #546

**Scenarios**

```gherkin
Given a membership with two supplies, only one of which stored a record at 13:00 on 10 January
When its hourly series is read for that day
Then 24 hours are emitted
And 13:00 reports two supplies, one of them with data, and that supply's energy and savings
And an hour in which neither supply stored a record has null savings and a null tariff source

Given a membership whose two supplies stored records at both 02:00 hours of 27 October 2024
When its hourly series is read for that day
Then 25 hours are emitted
And the two 02:00 hours have the start instants 02:00+02:00 and 02:00+01:00, each with its own figures
```

## Impossible states

- A daily series without any day — prevented by EPR-003: the start is before the start of the
  current hour, so the clipped period always overlaps at least one day.
- Two hours of the hourly series sharing a `startsAt` — prevented by MCS-003: the hours are counted
  on the instant line, one hour apart.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Disabled supplies are part of the membership's supplies: they count in the number of supplies and are summed when they stored data. | The daily and hourly series reuse the supply set of the membership monthly series and energy metrics, pending a decision on what a disabled supply means for a member's figures. | That decision is taken and applied to every membership series. | #410, #546 |
| Each supply's day is bucketed in the supply's time zone and folded into the community's day by date. | Both zones resolve to the application zone today, so the dates agree. | Supply or community time zones become configurable separately. | #410 |
| A period of the hourly series that holds no whole hour start (for example `[10:30, 10:45)`) returns no bucket. | EPR-001 only requires the start to be before the end, and every hour that starts inside such a period is emitted: there is none, as in the supply hourly series. | A minimum length or hour-aligned bounds are required. | #546 |
