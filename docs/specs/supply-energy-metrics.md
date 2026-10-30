# Spec — Supply energy metrics

| | |
| --- | --- |
| **Prefix** | `SEM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operation `getSupplyEnergyMetrics` |
| **Related ADRs** | — |
| **Last reviewed against code** | #411 |

## Purpose

The energy figures of a single supply over a period: energy totals, the self-sufficiency and
self-consumption ratios, coverage and savings. This spec covers how the period is resolved when the
server resolves it, and the self-consumption ratio; the rest of the capability is specified lazily, as
issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Published month (of a supply) | As defined by ENM-001, in the supply's time zone. |
| Plant's own supply | The supply a plant is attached to, where its production is measured. A supply is the own supply of at most one plant (PLT-002). The Datadis sync stores its surplus as 0 (`DatadisSyncServiceImpl#copyWithZeroSurplus`). |

## Rules

### SEM-001 — The latest published month is resolved from the supply's own records

- **Status:** Active
- **Rule:** Supply energy metrics MUST accept `period=LATEST_PUBLISHED_MONTH`, resolved with the
  criteria of ENM-001…005 applied to that supply's records alone: the latest of the 24 complete
  calendar months before the current one that is published for this supply, read in the supply's
  time zone, and the current month never. A month published only for another supply, of the same
  owner or not, MUST NOT be resolved. When no month qualifies, the response MUST be the one returned
  when no period can be resolved, as for a supply without any stored record. The reference period
  MUST NOT be combined with explicit dates; a request combining them is a bad request.
- **Rationale:** The historical analysis page anchors every period to the latest published month. The
  membership resolution accepts a month published for any of the member's supplies (ENM-002), so
  reusing it for one supply could show that supply a month it has not published, with its
  self-consumed energy reported as a measured zero. Resolving from the supply alone keeps a zero
  inside the resolved month a measured zero. The same threshold, window and current-month exclusion
  as ENM-001…003 keep a supply and its membership agreeing on what "published" means.
- **Source:** #408

**Scenarios**

```gherkin
Given a supply that published August but only part of September
And another supply of the same owner that published September
When the latest published month is requested for the first supply
Then the period is August

Given a supply whose only published month is the current one
When the latest published month is requested for it
Then no period is resolved
```

### SEM-002 — The self-consumption ratio is the supply's self-consumed energy over its assigned energy

- **Status:** Active
- **Rule:** The self-consumption ratio of a supply that is no plant's own supply MUST be its
  self-consumed energy divided by its assigned production (EVL-004) over the resolved period, both
  summed over every record before dividing once. When nothing was assigned, a period without any
  record included, the ratio MUST be null and reported as having nothing assigned, never as 0.
- **Rationale:** An hour consuming 100 kWh and an hour consuming 1 kWh must not weigh the same, so the
  ratio is a ratio of sums, never a mean of per-record ratios. Dividing by zero would produce a value
  that is not valid JSON.
- **Source:** #411

### SEM-003 — The self-consumption ratio does not apply to a plant's own supply

- **Status:** Active
- **Rule:** For a plant's own supply, whatever the resolution (explicit dates, the recorded span or
  `LATEST_PUBLISHED_MONTH`, a period resolved or not), the self-consumption ratio MUST be null and
  reported as not applicable, distinguishable from having nothing assigned. Whether a supply is a
  plant's own supply MUST be decided by the plant–supply link alone, never by its figures: a regular
  supply whose surplus is zero keeps its ratio. Every other figure (the energy totals, the
  self-sufficiency ratio, coverage and savings) MUST be reported exactly as for any other supply.
- **Rationale:** A plant's own supply has its Datadis surplus stored as 0, so its ratio would always
  be 1, which says nothing about how its assigned energy was used. A zero surplus is also what a regular supply that used everything reports, so only the
  link can tell the two apart. The other figures are still meaningful for that supply.
- **Source:** #411

### SEM-004 — Supply energy metrics expose the basis of the self-consumption ratio

- **Status:** Active
- **Rule:** Supply energy metrics MUST report the self-consumed and assigned energy the
  self-consumption ratio was computed from, rounded as every other kWh figure (EVL-001), and the
  number of plant's own supplies it leaves out: 0 for a regular supply, whose basis equals its
  totals, and 1 for a plant's own supply, whose basis energy is then 0.
- **Rationale:** A client shows the ratio together with the energy it divides ("X of Y kWh assigned")
  and must never recompute a ratio itself; the count tells it why the ratio is missing.
- **Source:** #411

**Scenarios**

```gherkin
Given a plant's own supply that self-consumed 4 kWh and stored no surplus over a day
When its energy metrics are read for that day
Then its self-consumption ratio is null and not applicable
And its basis is 0 kWh of 0 kWh, with 1 plant supply left out
And its self-consumed energy is still 4 kWh

Given a supply no plant uses that self-consumed 4 kWh and stored no surplus over a day
When its energy metrics are read for that day
Then its self-consumption ratio is 1
```

## Impossible states

- A resolved month equal to the current month — prevented by SEM-001 (the search window ends before
  the current month).
