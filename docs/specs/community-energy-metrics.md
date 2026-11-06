# Spec — Community energy metrics

| | |
| --- | --- |
| **Prefix** | `CEM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Communities`; operations `getCommunityEnergyMetrics`, `getCommunityHourlyEnergySeries`, `getCommunityDailyEnergySeries`, `getCommunityMonthlyEnergySeries` |
| **Related ADRs** | — |
| **Last reviewed against code** | #554 |

## Purpose

The energy figures of a whole community, from the distributor's records: what its plants produced,
how much of the energy assigned to its supplies was used in the community and how much went to the
grid, the installed power, and how many supplies have data. Only community aggregates are exposed,
never a figure of a single supply or member. The period these operations are computed over follows
EPR-001 to EPR-005 (`energy-periods.md`), and the precision and names of their energy figures follow
`energy-values.md`. The rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Supplies in scope | Every enabled supply of the community, whatever the membership status of its owner (CEM-002). A plant's own supply is one of them. |
| Published record | As in ENM: an hourly record that carries self-consumed energy, zero included. |
| Supply with data (for a period) | A supply whose published records cover at least 90 % of the possible hours of the period (CEM-004). |
| Possible hours | The hours between the bounds of the period, as clipped under EPR-002, counted on the instant line, so a day with a daylight saving change has 23 or 25. |
| Gap | A figure nothing was stored for. It is null, never 0. |
| Measured zero | A figure whose stored records carry no energy. It is 0. |

## Rules

### CEM-001 — The community anchor is the latest month published for any supply in scope

- **Status:** Active
- **Rule:** `period=LATEST_PUBLISHED_MONTH` on community energy metrics MUST resolve to the latest of
  the 24 complete months before the current one that is published (ENM-001) for at least one supply
  in scope. The current month MUST never be returned. With no such month the response MUST be 200
  with null period bounds.
- **Rationale:** Same anchor semantics as the membership, so community and member figures line up.
- **Source:** #554

**Scenarios**

```gherkin
Given a community where supply S1 is published for September and S2 only for August
When an active member requests its energy metrics with period=LATEST_PUBLISHED_MONTH
Then the resolved period is September
And the coverage reports 1 of 2 supplies with data

Given a community with no published month in the last 24 complete months
When its energy metrics are requested with period=LATEST_PUBLISHED_MONTH
Then the response is 200 with null period bounds and no figures
```

### CEM-002 — The supplies in scope are every enabled supply of the community

- **Status:** Active
- **Rule:** The supplies in scope MUST be every enabled supply of the community, whatever the
  membership status of its owner.
- **Rationale:** The energy flows regardless of membership status. Otherwise a closed month's figures
  would change when a member leaves.
- **Source:** #554

### CEM-003 — Totals are sums, and the shares are computed from self-consumption and surplus only

- **Status:** Active
- **Rule:** Totals MUST be sums over the supplies in scope (self-consumption, surplus) and over the
  community's plants (production). The used and exported shares MUST be computed from
  self-consumption and surplus only, on unrounded values, and MUST be null when both are zero.
- **Rationale:** The two shares must add up to 1, independently of the losses between plant export
  and assigned energy.
- **Source:** #554

**Scenarios**

```gherkin
Given supplies in scope with 60 kWh of self-consumption and 40 kWh of surplus in total
When the community energy metrics are read
Then the shares are 0.6 used in the community and 0.4 exported

Given supplies in scope whose records carry no self-consumption and no surplus
When the community energy metrics are read
Then both shares are null
```

### CEM-004 — Coverage counts the supplies with data under the 90 % threshold

- **Status:** Active
- **Rule:** Coverage MUST report the number of supplies in scope and how many have data for the
  resolved period. A supply has data when its records carrying self-consumption cover at least 90 %
  of the possible hours of the period.
- **Rationale:** It drives the coverage notice on the page. The same threshold as ENM-001 keeps
  "published" and "with data" consistent.
- **Source:** #554

**Scenarios**

```gherkin
Given a period of 720 possible hours
When a supply has records carrying self-consumption in 648 of them
Then it counts as a supply with data
When a supply has records carrying self-consumption in 647 of them
Then it does not
```

### CEM-005 — Every plant is listed, and a plant without records has no production

- **Status:** Active
- **Rule:** Metrics MUST list every plant of the community with its reference, installed power (kWp),
  production and share of total production. A plant with no production records in the period MUST
  report null production and null share.
- **Rationale:** A gap is not a measured zero.
- **Source:** #554

### CEM-006 — A series emits every bucket, with null for a gap and the supplies with data

- **Status:** Active
- **Rule:** Each series MUST emit every bucket of the clipped period. A bucket with no supply data MUST
  carry null energy values, and every bucket MUST report how many supplies have data in it.
- **Rationale:** Same gap, partial and zero distinction as MCS-001…003.
- **Source:** #554

**Scenarios**

```gherkin
Given a community with three supplies in scope, none of which has data on 10 May
When its daily energy series is read from 10 to 12 May
Then 10 May is emitted with null energy values and 0 supplies with data

Given a community with three supplies in scope, two of which have data on 11 May
When its daily energy series is read for that day
Then the day reports two supplies with data and the sum of their energy
```

### CEM-007 — Members read community aggregates only

- **Status:** Active
- **Rule:** These operations MUST be readable by any active member of the community, MUST answer 403
  to a platform admin without a membership and 404 to anyone else, and MUST NOT expose any supply id,
  CUPS, member reference or per-supply figure.
- **Rationale:** Members see community aggregates only. Per-supply data stays behind `canReadSupply`.
- **Source:** #554

## Impossible states

- A share above 1, or two shares that do not add up to 1 — prevented by CEM-003: both are computed
  from the same two unrounded sums.
- A resolved anchor in the current month — prevented by CEM-001 (the search window ends before the
  current month).
- A daily series without any day — prevented by EPR-003: the start is before the start of the
  current hour, so the clipped period always overlaps at least one day.
- Two hours of the hourly series sharing a `startsAt` — prevented by CEM-006 together with EPR-001:
  the hours are counted on the instant line, one hour apart.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| In the monthly series, a supply has data in a month when it stored a monthly pre-aggregate for it; the 90 % threshold is not applied, and a month aggregated before the distributor published its self-consumption reports a measured 0 rather than a gap. | The monthly series reads the monthly pre-aggregates, as the membership monthly series does, and they keep no count of the hours carrying self-consumption and store missing self-consumption as 0. | The monthly pre-aggregate stores the number of hours carrying self-consumption, or the series reads the hourly records. | #554 |
| A period of the hourly series that holds no whole hour start (for example `[10:30, 10:45)`) returns no bucket, and a monthly series whose period holds no local midnight of a day 1 returns none either. | EPR-001 only requires the start to be before the end; every bucket that starts inside such a period is emitted, and there is none. | A minimum length or aligned bounds are required. | #554 |
| `supplyCount` is the number of supplies in scope today, the same for every bucket and period, including buckets before a supply joined. | The model records no date a supply joined or left its community or was disabled. | Supply membership in a community gets a validity interval. | #554 |
| With neither dates nor `period`, the metrics period spans the stored records of the supplies in scope, as the membership metrics do. | Mirrors the period parameters of `getMembershipEnergyMetrics`; the recorded-period fallback is the one EPR already lists as an accepted exception. | The recorded-period fallback is capped or removed. | #409, #554 |
