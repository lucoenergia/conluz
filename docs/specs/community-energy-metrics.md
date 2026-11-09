# Spec — Community energy metrics

| | |
| --- | --- |
| **Prefix** | `CEM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Communities`; operations `getCommunityEnergyMetrics`, `getCommunityHourlyEnergySeries`, `getCommunityDailyEnergySeries`, `getCommunityMonthlyEnergySeries` |
| **Related ADRs** | — |
| **Last reviewed against code** | #559 |

## Purpose

The energy figures of a whole community, from the distributor's records: what its plants produced,
how much of the energy assigned to its supplies was used in the community and how much went to the
grid, an estimate of what that surplus earned at OMIE day-ahead prices, an estimate of the CO₂ the
production avoided, the installed power, and how many supplies have data. Only community aggregates are exposed, never a figure of a single supply or
member. The period these operations are computed over follows EPR-001 to EPR-005
(`energy-periods.md`), and the precision and names of their energy figures follow `energy-values.md`.
The rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Supplies in scope | Every enabled supply of the community, whatever the membership status of its owner (CEM-002). A plant's own supply is one of them. |
| Published record | As in ENM: an hourly record that carries self-consumed energy, zero included. |
| Supply with data (for a period) | A supply whose published records cover at least 90 % of the possible hours of the period (CEM-004). |
| Possible hours | The hours between the bounds of the period, as clipped under EPR-002, counted on the instant line, so a day with a daylight saving change has 23 or 25. |
| Gap | A figure nothing was stored for. It is null, never 0. |
| Measured zero | A figure whose stored records carry no energy. It is 0. |
| Emission factor | The MITECO "Mix sin GdO" electricity emission factor of a calendar year, in kg CO₂e/kWh (CEM-015). |
| Applied factor year | The year whose emission factor is applied to a calendar year of the period: that year, or the latest earlier year with a factor (CEM-012). |

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

### CEM-008 — The surplus income is each hour's surplus priced at that hour's mean OMIE price

- **Status:** Active
- **Rule:** The estimated surplus income on community energy metrics MUST be the sum, over every hour
  of the resolved period, of that hour's surplus multiplied by the mean of the OMIE prices stored for
  that hour, with negative and zero prices applied as they are. It MUST be summed unrounded and
  rounded once, with the rule used for `savings.amountEur`.
- **Rationale:** A market-price estimate of what the exported energy earned. Hourly pricing follows
  the hourly Datadis surplus. Since October 2025, the mean of the four quarter-hour prices is the
  hourly equivalent.
- **Source:** #556

**Scenarios**

```gherkin
Given supplies in scope with 2 kWh and 3 kWh of surplus in the same hour
And four quarter-hour prices of 0.04, 0.06, 0.08 and 0.10 €/kWh stored for that hour
When the community energy metrics are read
Then that hour adds 5 kWh × 0.07 €/kWh = 0.35 € to the surplus income

Given 4 kWh of surplus in an hour whose stored price is -0.01 €/kWh
When the community energy metrics are read
Then that hour adds -0.04 € to the surplus income
```

### CEM-009 — The surplus of an hour without a price is reported as unpriced

- **Status:** Active
- **Rule:** An hour with surplus and no stored OMIE price MUST add nothing to the income, and its
  surplus MUST be reported as unpriced energy in the same response.
- **Rationale:** A missing price must be visible, not hidden as a lower income.
- **Source:** #556

**Scenarios**

```gherkin
Given 1.5 kWh of surplus in an hour without any stored OMIE price
When the community energy metrics are read
Then that hour adds nothing to the surplus income
And the unpriced surplus is 1.5 kWh
```

### CEM-010 — The income covers exactly the surplus reported next to it

- **Status:** Active
- **Rule:** The income and the unpriced energy MUST be computed over exactly the surplus that makes up
  the same response's `surplusKWh`, and MUST be null when that figure is null.
- **Rationale:** The income must match the energy shown next to it.
- **Source:** #556

### CEM-011 — CO₂ avoided is each local year's production at that year's factor, rounded once

- **Status:** Active
- **Rule:** CO₂ avoided on community energy metrics MUST be the sum, over each local calendar year of
  the resolved period, of that year's plant production multiplied by the emission factor applied to
  that year, rounded once to 3 decimals.
- **Rationale:** Factors are published per year, and a period can span years.
- **Source:** MITECO, Factores de emisión v6 (2026-05-07); #559

**Scenarios**

```gherkin
Given factors of 0.200 for 2024 and 0.150 for 2025
And a period from 2024-11-01 to 2025-03-01 in the community's time zone
And 400 kWh produced in 2024 and 600 kWh in 2025, in that time zone
When the community energy metrics are read
Then CO₂ avoided is 400 × 0.200 + 600 × 0.150 = 170 kg

Given a record at 00:30 on 1 January 2025 in the community's time zone, 23:30 on 31 December 2024 in UTC
When the community energy metrics are read
Then its production counts in 2025

Given a year inside the period with no production record
When the community energy metrics are read
Then that year does not affect CO₂ avoided or the applied factor years

Given a year inside the period whose only production records are zero
When the community energy metrics are read
Then that year counts, with its applied factor year
```

### CEM-012 — A year without a factor uses the latest earlier one, and none earlier makes CO₂ avoided null

- **Status:** Active
- **Rule:** A year with no configured factor MUST use the latest earlier configured factor. If no
  earlier factor exists, CO₂ avoided MUST be null.
- **Rationale:** The current year's factor is published late. A figure must never be computed with a
  factor from a later year.
- **Source:** MITECO, Factores de emisión v6 (2026-05-07); #559

**Scenarios**

```gherkin
Given factors configured up to 2025
And a period inside 2026 with production
When the community energy metrics are read
Then the 2025 factor is applied and the applied factor years are [2025]

Given factors configured from 2021
And a production record in 2020 inside the period
When the community energy metrics are read
Then CO₂ avoided is null
```

### CEM-013 — The applied factor years are reported in ascending order

- **Status:** Active
- **Rule:** The response MUST report the factor years applied, in ascending order.
- **Rationale:** The page names the factor year, and a reader must be able to tell which factor was
  used.
- **Source:** MITECO, Factores de emisión v6 (2026-05-07); #559

### CEM-014 — CO₂ avoided is null when the production is

- **Status:** Active
- **Rule:** CO₂ avoided and the applied factor years MUST be null when the plant production is null.
- **Rationale:** A gap is not a zero.
- **Source:** MITECO, Factores de emisión v6 (2026-05-07); #559

### CEM-015 — The factors are the MITECO "Mix sin GdO" factors, validated at startup

- **Status:** Active
- **Rule:** The emission factors MUST be the MITECO "Mix sin GdO" electricity factors in kg CO₂e/kWh,
  configured per year from 2021. The application MUST refuse to start without at least one factor, or
  with a negative one.
- **Rationale:** MITECO prescribes this factor when the retailer is unknown. No data earlier than 2021
  exists. A missing or wrong factor must fail at deploy time, not show a wrong figure.
- **Source:** MITECO, Factores de emisión v6 (2026-05-07); #559

### CEM-016 — The configured factors keep up with the MITECO publication calendar

- **Status:** Active
- **Rule:** From 1 June of year Y (Europe/Madrid), the configured factors MUST include year Y − 1, and
  before that date year Y − 2. A scheduled check outside the regular build MUST fail when they do not.
- **Rationale:** MITECO publishes each year's factors in May of the following year. A date must never
  break an unrelated build, but an outdated factor must not go unnoticed.
- **Source:** MITECO, Factores de emisión v6 (2026-05-07); #559

**Scenarios**

```gherkin
Given factors configured up to 2025
When the configured factors are checked on 2027-05-31
Then the check passes

Given factors configured up to 2025
When the configured factors are checked on 2027-06-01
Then the check fails, naming 2026

Given factors configured up to 2026
When the configured factors are checked on 2027-06-01
Then the check passes
```

**Operational note:** when the scheduled check fails, take the new MITECO "Factores de emisión"
version, update every year it revises, and redeploy.

## Impossible states

- A share above 1, or two shares that do not add up to 1 — prevented by CEM-003: both are computed
  from the same two unrounded sums.
- A resolved anchor in the current month — prevented by CEM-001 (the search window ends before the
  current month).
- A daily series without any day — prevented by EPR-003: the start is before the start of the
  current hour, so the clipped period always overlaps at least one day.
- Two hours of the hourly series sharing a `startsAt` — prevented by CEM-006 together with EPR-001:
  the hours are counted on the instant line, one hour apart.
- A priced and an unpriced surplus that do not add up to `surplusKWh`, or an income without a
  `surplusKWh` — prevented by CEM-010: both are read over the same supplies and the same interval, and
  computed only when `surplusKWh` is.
- CO₂ avoided computed from production other than `productionKWh` — prevented by CEM-011: the
  production per year is read over the same plants' supplies and the same interval as
  `productionKWh`.
- An applied factor year later than the year it is applied to — prevented by CEM-012.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A period of the hourly series that holds no whole hour start (for example `[10:30, 10:45)`) returns no bucket, and a monthly series whose period holds no local midnight of a day 1 returns none either. | EPR-001 only requires the start to be before the end; every bucket that starts inside such a period is emitted, and there is none. | A minimum length or aligned bounds are required. | #554 |
| `supplyCount` is the number of supplies in scope today, the same for every bucket and period, including buckets before a supply joined. | The model records no date a supply joined or left its community or was disabled. | Supply membership in a community gets a validity interval. | #554 |
| With neither dates nor `period`, the metrics period spans the stored records of the supplies in scope, as the membership metrics do. | Mirrors the period parameters of `getMembershipEnergyMetrics`; the recorded-period fallback is the one EPR already lists as an accepted exception. | The recorded-period fallback is capped or removed. | #409, #554 |
| Hours stored before #322 and never re-synced sit at the end of their hour (see the accepted exception of [`datadis-hourly-ingestion.md`](datadis-hourly-ingestion.md)), so CEM-008 prices them one hour late. The daily Datadis sync re-reads only its 13-month window, so months older than that keep the late placement unless they are re-synced manually. | The fix of #322 applies to records as they are synced, and the prices are matched to the hour a record is stored at. | No month inside the 24-month maximum period was stored before #322 without a later re-sync. | #322, #556 |
