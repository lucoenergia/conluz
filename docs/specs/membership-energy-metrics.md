# Spec — Membership energy metrics

| | |
| --- | --- |
| **Prefix** | `ENM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Memberships`; operations `getMembershipEnergyMetrics`, `getMembershipHourlyProfile` |
| **Related ADRs** | — |
| **Last reviewed against code** | #382 |

## Purpose

The energy figures of a member's supplies in one community, added up: the aggregated energy metrics
and the hourly profile the member home is built around. This spec covers only how the reference
month those figures describe is resolved; the rest of the capability is specified lazily, as issues
touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Published record | An hourly record that carries self-consumed energy, zero included. Datadis sends a month's consumption and the surplus the meter measured as the days go by, but self-consumed energy only once it publishes the month. |
| Published month (of a supply) | A complete calendar month, in the community's time zone, in which the supply's published records cover at least 90 % of the hours it could have published (ENM-001). |
| Reference month | The month `LATEST_PUBLISHED_MONTH` resolves to, and the only month the hourly profile reports on (ENM-002). |
| Coverage | The hourly consumption records found against the hours the period spans times the supplies of the membership. It measures consumption records, not publication. |

## Rules

### ENM-001 — A month is published for a supply when 90 % of its possible hours are published

- **Status:** Active
- **Rule:** A month MUST count as published for a supply when its published records number at least
  90 % of the hours from the later of the month's first hour and the supply's first published record
  to the end of the month, counted in the community's time zone. A record carrying surplus but no
  self-consumed energy MUST NOT count as published, however large the surplus.
- **Rationale:** Before #382 a month counted as published as soon as one record carried
  self-consumed or surplus energy above zero. Datadis sends the measured surplus from the first day,
  so a month holding 0.198 kWh of surplus over its first twelve days was resolved and the member home
  reported a 0 % share for it. Measured on the production dataset, published months have ≥ 99.9 % of
  each supply's hours carrying self-consumed energy and the unpublished month 0 %; 90 % tolerates
  about three days of distributor gaps while rejecting a month whose last days are still missing.
  Judging from the supply's first published record keeps a supply whose assigned production started
  mid-month from having its first month skipped for good. The threshold is a constant,
  `ReferenceMonthResolver.PUBLISHED_HOURS_MIN_PERCENT`, stated in both operation descriptions: a
  change in Datadis's publication rhythm changes when a month reaches it, not what it means.
- **Source:** #382

**Scenarios**

```gherkin
Given August is published in full and September holds consumption and measured surplus but no self-consumption
When the reference month is resolved
Then August is resolved

Given a supply whose first published record is on 15 August and every later August hour is published
When the reference month is resolved
Then August is resolved, although 408 of its 744 hours are published
```

### ENM-002 — The reference month is the latest month published for any supply in the window

- **Status:** Active
- **Rule:** The reference month MUST be the latest of the 24 complete calendar months before the
  current one that is published for at least one of the member's supplies in the community. The
  current month MUST NOT be a candidate. A month that qualifies MUST be resolved as soon as it does.
- **Rationale:** "Any supply" rather than "every supply" or a pooled share: a member may hold a supply
  that never publishes self-consumed energy (a plant's own supply, or one that left the sharing), and
  requiring it would leave that member without any month. This is a watched assumption, recorded
  under *Accepted exceptions*.
- **Source:** #382

### ENM-003 — No published month resolves no period

- **Status:** Active
- **Rule:** When no month in the search window is published for any of the member's supplies,
  including when the membership has no supplies, no reference month MUST be resolved: both
  operations answer successfully with null period bounds.
- **Rationale:** Falling back to the best partly published month would bring back the defect of
  #382 under another name, and would break the hourly profile's premise that a stored zero inside the
  resolved month is a measured zero.
- **Source:** #382

### ENM-004 — Both operations resolve the same month

- **Status:** Active
- **Rule:** For the same membership at the same instant, `getMembershipHourlyProfile` MUST resolve
  the same month as `getMembershipEnergyMetrics` with `period=LATEST_PUBLISHED_MONTH`.
- **Rationale:** The member home shows both side by side; one resolver serves both, so they agree by
  construction.
- **Source:** #382

### ENM-005 — Coverage is still reported for the resolved month

- **Status:** Active
- **Rule:** Coverage MUST still be reported for the resolved month, including the hours without a
  record of a month that qualified under ENM-001.
- **Rationale:** ENM-001 chooses the month; it does not replace the signal of scattered gaps within
  it.
- **Source:** #382

## Impossible states

- A reference month equal to the current month — prevented by ENM-002 (the search window ends before
  the current month).

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A month published for one of a member's supplies is resolved while another of them is still unpublished; its assigned production is then missing from the figures and coverage, which counts consumption records, does not show it. | Datadis publishes a distributor's month in one batch, so a member's supplies normally publish together; requiring every supply would leave members holding a never-publishing supply without any month. | A member's supplies are seen publishing a month at different times, for example because they belong to different distributors. | #382 |
