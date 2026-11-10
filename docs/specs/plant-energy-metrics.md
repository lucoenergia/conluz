# Spec — Plant energy metrics

| | |
| --- | --- |
| **Prefix** | `PEM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Plants`; operations `getPlantEnergyMetrics`, `getPlantHourlyEnergySeries`, `getPlantDailyEnergySeries`, `getPlantMonthlyEnergySeries` |
| **Related ADRs** | — |
| **Last reviewed against code** | #561 |

## Purpose

The energy figures of a single plant, from the distributor's records of the plant's backing supply:
its production over a period, per hour, day and month, how much of each period its records cover, and
the latest complete day of production the plant detail anchors on. These figures are for the
community's admins. The period these operations are computed over follows EPR-001 to EPR-005
(`energy-periods.md`), and the precision and names of their energy figures follow `energy-values.md`.
The rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Backing supply | The supply a plant is connected through (PLT-002). Its Datadis surplus is stored as the plant's production, one record per hour Datadis returns (DHI-001…003). |
| Production record | An hourly record of the backing supply's production. An hour without surplus is stored as a record of 0. |
| Possible hours | The hours of a period or bucket inside the period, as clipped under EPR-002, counted on the instant line, so a day with a daylight saving change has 23 or 25. |
| Complete day | A local day of the community's time zone whose production records cover all its possible hours. |
| Gap | A figure nothing was stored for. It is null, never 0. |
| Measured zero | A figure whose stored records carry no energy, such as at night. It is 0. |

## Rules

### PEM-001 — The plant anchor is the latest complete local day before today

- **Status:** Active
- **Rule:** `period=LATEST_PRODUCTION_DAY` on plant energy metrics MUST resolve to the latest local day
  before the current one, within the 24 months before today, whose stored production records cover
  all its hours. The current local day MUST never be returned. With no such day the response MUST be
  200 with null period bounds.
- **Rationale:** Production arrives daily, so the plant page anchors on the last complete day, not on
  the last published month. A partial day would make the last day of every preset look low, and the
  current day is not over.
- **Source:** #561

**Scenarios**

```gherkin
Given night hours are stored as records of 0, so a complete day has 23, 24 or 25 records, of which the night ones are 0
And a plant whose records cover every hour of 2026-11-07, night zeros included
And whose 2026-11-08 has only some of its hours
And whose 2026-11-10, the current day, has every hour
When its energy metrics are read with period=LATEST_PRODUCTION_DAY
Then the period is [2026-11-07T00:00, 2026-11-08T00:00) in the community's time zone
```

```gherkin
Given a plant whose 2026-11-09 has every hour but the night hour 03:00
When its energy metrics are read with period=LATEST_PRODUCTION_DAY
Then 2026-11-09 is not complete, and an earlier complete day is resolved
```

### PEM-002 — Plant production is the backing supply's hourly production, null without a record

- **Status:** Active
- **Rule:** Plant production MUST be the Datadis production of the plant's backing supply, read from
  hourly records, and MUST be null when the period holds no record.
- **Rationale:** It is the energy the plant exported, as the distributor measured it. A gap is not a
  zero.
- **Source:** #561

### PEM-003 — Plant series emit every bucket, with gaps as null and coverage per bucket

- **Status:** Active
- **Rule:** Each series MUST emit every bucket of the clipped period, with null production for a
  bucket without records. Every bucket MUST report its possible hours inside the period and its hours
  with a record. Monthly buckets MUST be built from hourly records.
- **Rationale:** Same gap, partial and zero distinction as CEM-006. Pre-aggregates cannot tell an
  unpublished hour from a zero.
- **Source:** #561

### PEM-004 — Plant energy is readable by the community's admins only, and exposes no supply

- **Status:** Active
- **Rule:** These operations MUST be readable only by a community admin of the plant's community. They
  MUST answer 403 to a member of that community and 404 to anyone else, and MUST NOT expose any CUPS or
  supply id.
- **Rationale:** The plant detail is an admin view. Members see per-plant totals on the community
  page.
- **Source:** #561

## Impossible states

- A gap reported as 0, or a measured zero reported as null — prevented by PEM-002 and PEM-003:
  production is null exactly when the period or bucket holds no record.
- The current local day resolved as the latest production day — prevented by PEM-001: its records are
  never read for the anchor.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A period resolved from the span of the plant's stored records is neither capped nor clipped. | It is not a requested range, as for the other energy operations (EPR accepted exceptions). | The recorded-period fallback is capped or removed. | #561 |
