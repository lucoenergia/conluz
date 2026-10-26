# Spec — Supply energy metrics

| | |
| --- | --- |
| **Prefix** | `SEM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operation `getSupplyEnergyMetrics` |
| **Related ADRs** | — |
| **Last reviewed against code** | #408 |

## Purpose

The energy figures of a single supply over a period: energy totals, the self-sufficiency and
self-consumption ratios, coverage and savings. This spec covers only how the period is resolved when
the server resolves it; the rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Published month (of a supply) | As defined by ENM-001, in the supply's time zone. |

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

## Impossible states

- A resolved month equal to the current month — prevented by SEM-001 (the search window ends before
  the current month).
