# Spec — Membership energy metrics

| | |
| --- | --- |
| **Prefix** | `ENM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Memberships`; operations `getMembershipEnergyMetrics`, `getMembershipHourlyProfile` |
| **Related ADRs** | — |
| **Last reviewed against code** | #411 |

## Purpose

The energy figures of a member's supplies in one community, added up: the aggregated energy metrics
and the hourly profile the member home is built around. This spec covers how the reference month
those figures describe is resolved, and the self-consumption ratio of the aggregated energy metrics;
the rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Published record | An hourly record that carries self-consumed energy, zero included. Datadis sends a month's consumption and the surplus the meter measured as the days go by, but self-consumed energy only once it publishes the month. |
| Published month (of a supply) | A complete calendar month, in the community's time zone, in which the supply's published records cover at least 90 % of the hours it could have published (ENM-001). |
| Reference month | The month `LATEST_PUBLISHED_MONTH` resolves to, and the only month the hourly profile reports on (ENM-002). |
| Coverage | The hourly consumption records found against the hours the period spans times the supplies of the membership. It measures consumption records, not publication. |
| Plant's own supply | As defined in SEM: the supply a plant is attached to, whose Datadis surplus is stored as 0. |

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

### ENM-006 — The membership self-consumption ratio is computed once over its regular supplies

- **Status:** Active
- **Rule:** The self-consumption ratio of a membership's energy metrics MUST be the self-consumed
  energy of its supplies that are no plant's own supply, summed, divided once by their summed
  assigned production; it MUST NOT be an average of per-supply ratios. When nothing was assigned to
  those supplies, a period without data and a reference month that does not resolve included, the
  ratio MUST be null and reported as having nothing assigned.
- **Rationale:** A supply consuming 100 kWh and one consuming 1 kWh must not weigh the same.
- **Source:** #411

### ENM-007 — Plant's own supplies are left out of the membership self-consumption ratio

- **Status:** Active
- **Rule:** A plant's own supply, identified by the plant–supply link alone, MUST be left out of both
  the numerator and the denominator of the membership self-consumption ratio, under every resolution.
  When every supply of the membership is a plant's own supply, the ratio MUST be null and reported as
  not applicable, which takes precedence over having nothing assigned. Plant's own supplies MUST keep
  counting in the energy totals, the self-sufficiency ratio, coverage and savings.
- **Rationale:** A plant's own supply has its Datadis surplus stored as 0, so its ratio is always 1
  and counting it inflates the ratio of any member who owns one: 100 of 200 kWh on a regular supply
  and 50 of 50 on a plant's supply would read 0.6 instead of 0.5. The other figures describe energy
  the member really consumed or saved, so they keep it.
- **Source:** #411

### ENM-008 — Membership energy metrics expose the basis of the self-consumption ratio

- **Status:** Active
- **Rule:** Membership energy metrics MUST report the summed self-consumed and assigned energy the
  self-consumption ratio was computed from, rounded as every other kWh figure (EVL-001), and the
  number of plant's own supplies it leaves out. When the ratio is not applicable, both figures MUST be
  0 and the count MUST be the number of plant's own supplies. Without plant's own supplies the basis
  equals the energy totals.
- **Rationale:** The member home shows the ratio with its "X of Y kWh assigned" line; once supplies
  are left out, the totals no longer are that line, and the client must not recompute a ratio.
- **Source:** #411

**Scenarios**

```gherkin
Given a member with a regular supply that self-consumed 100 of 200 kWh assigned
And a plant's own supply that self-consumed 50 of 50 kWh assigned
When the membership energy metrics are read
Then the self-consumption ratio is 0.5
And its basis is 100 kWh of 200 kWh, with 1 plant supply left out
And the self-consumed energy total is 150 kWh

Given a member whose only supplies are two plant's own supplies
When the membership energy metrics are read
Then the self-consumption ratio is null and not applicable, with 2 plant supplies left out
```

## Impossible states

- A reference month equal to the current month — prevented by ENM-002 (the search window ends before
  the current month).

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A month published for one of a member's supplies is resolved while another of them is still unpublished; its assigned production is then missing from the figures and coverage, which counts consumption records, does not show it. | Datadis publishes a distributor's month in one batch, so a member's supplies normally publish together; requiring every supply would leave members holding a never-publishing supply without any month. | A member's supplies are seen publishing a month at different times, for example because they belong to different distributors. | #382 |
