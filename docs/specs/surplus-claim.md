# Spec — Surplus claim

| | |
| --- | --- |
| **Prefix** | `SCL` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Plants`; operation `getPlantSurplusClaim` |
| **Related ADRs** | — |
| **Last reviewed against code** | #629 |

## Purpose

Every month the community admin claims from the retailer the surplus of each production installation, to get paid
for it. The surplus claim is the computation behind that claim: a plant's surplus for one closed month, hour by hour,
with what is missing from it, so the admin can see what would be sent before sending it. It is computed on demand and
never stored.

## Glossary

| Term | Meaning |
| --- | --- |
| Applied coefficient | A partition coefficient with a validFrom: one the distributor applies. Its period is half-open (SHA-002). A pending coefficient is not applied. |
| Supply in scope | A supply whose surplus a month's claim sums (SCL-001). |
| Surplus record | An hourly Datadis record of a supply carrying surplus, zero included. A record without the field is not one. |
| In force on a day | An applied coefficient whose period contains the local midnight the day starts at. |
| Closed month | A calendar month before the current one, in the zone of the plant's community. |

## Rules

### SCL-001 — The supplies in scope share the plant's production in the month

- **Status:** Active
- **Rule:** The supplies in scope of a month MUST be the supplies holding an applied coefficient of the plant whose
  period overlaps the month, the plant's own supply excluded. Their surplus MUST be summed over the whole month,
  whatever part of it their coefficient covers.
- **Rationale:** Only those supplies share the plant's surplus that month. The plant's own supply reports the plant's
  production, not a surplus: the sync stores its surplus as 0.
- **Source:** #629

### SCL-002 — An hour is the sum of the records in scope, null without any

- **Status:** Active
- **Rule:** An hour's surplus MUST be the sum of the surplus records of the supplies in scope for that hour. It MUST be
  null when none of them has a surplus record for it; a record carrying 0 MUST count as a record.
- **Rationale:** A missing record is not a measured zero. The retailer has to be told which hours are measured.
- **Source:** #629

**Scenarios**

```gherkin
Given two supplies in scope
And only the first has a record at 10:00, carrying 0 kWh
And neither has a record at 11:00
When the month is claimed
Then 10:00 is 0 kWh and 11:00 is null
```

### SCL-003 — A day is REAL only when every supply in scope covers every hour

- **Status:** Active
- **Rule:** A day MUST be `REAL` only when every supply in scope has a surplus record for every hour of the day, and
  `ESTIMATED` otherwise. With no supply in scope, every day MUST be `ESTIMATED`.
- **Rationale:** The retailer is told which days are estimated.
- **Source:** #629

### SCL-004 — Days and hours are local to the plant's community

- **Status:** Active
- **Rule:** Every local day of the month MUST be listed with its 23, 24 or 25 hours in the zone of the plant's
  community, in order, each with the instant it starts at.
- **Rationale:** The days the clocks change have 23 and 25 hours, as the stored records do (DHI-001).
- **Source:** #629

### SCL-005 — A day's total and peak hour

- **Status:** Active
- **Rule:** A day's surplus MUST be the sum of its hours, null when every hour is null. Its peak hour MUST be the
  earliest of the hours holding the day's largest surplus, given with its start and its surplus, and MUST be null
  when the day's surplus is null or 0.
- **Rationale:** The peak is what a reviewer checks against the plant's power; a day without surplus has none.
- **Source:** #629

### SCL-006 — The monthly summary and its income

- **Status:** Active
- **Rule:** The month's surplus MUST be the sum of its hours, null when no supply in scope has a surplus record in
  the month. The summary MUST count the `REAL` and `ESTIMATED` days, the supplies in scope, and those of them with
  at least one surplus record in the month. The estimated income and the unpriced surplus MUST follow CEM-008 to
  CEM-010 over exactly the month's hourly sums, and MUST be null when the month's surplus is null.
- **Rationale:** One pricing rule across the application, applied to exactly the energy shown next to it.
- **Source:** #629, CEM-008, CEM-009, CEM-010

### SCL-007 — The claimed month

- **Status:** Active
- **Rule:** Without a requested month, the claim MUST be for the latest published month (SCL-008), else for the last
  closed month. The current month and later ones, in the zone of the plant's community, MUST be refused with 400
  `SURPLUS_CLAIM_MONTH_NOT_CLOSED`. A closed month outside the selectable range MUST be answered normally.
- **Rationale:** Only closed months can be claimed. A month outside the range is still answerable: its warnings say
  why it is empty.
- **Source:** #629

### SCL-008 — The selectable range

- **Status:** Active
- **Rule:** The first selectable month MUST be the month of the plant's earliest applied coefficient. The latest
  published month MUST be the one ENM-002 resolves over every supply that ever held an applied coefficient of the
  plant, the plant's own supply excluded. Each bound MUST be null when it cannot be resolved.
- **Rationale:** The admin picks a month between the plant's first sharing and the latest month Datadis has
  published.
- **Source:** #629, ENM-002

### SCL-009 — Supplies without data and supplies with gaps

- **Status:** Active
- **Rule:** A supply in scope without any surplus record in the month MUST be listed in `suppliesWithoutData`, with
  the coefficient in force on the last day of the month on which it held one. A supply in scope with surplus records
  in the month, but not for every hour of some days, MUST be listed in `suppliesWithGaps` with those days.
- **Rationale:** The admin has to see whose surplus is missing, and how much of the production that supply was owed.
- **Source:** #629

### SCL-010 — Uncovered share

- **Status:** Active
- **Rule:** When applied coefficients of the plant overlap the month, `uncoveredShare` MUST be raised on the days whose
  coefficients in force, the plant's own supply included, add up to less than 1 − 0.0001, with those days and the
  largest uncovered share. A day with no coefficient in force MUST count as fully uncovered. When no applied
  coefficient overlaps the month, `uncoveredShare` MUST be null.
- **Rationale:** Part of the production nobody is assigned is surplus nobody claims. Without any applied coefficient,
  `noAppliedCoefficients` already reports it, and a share of 1 on every day would only repeat it.
- **Source:** #629

### SCL-011 — Plant supply share, no applied coefficients, hours above nominal power

- **Status:** Active
- **Rule:** `plantSupplyShare` MUST be the coefficient of the plant's own supply when it holds an applied coefficient
  overlapping the month, and null otherwise. `noAppliedCoefficients` MUST be true exactly when no applied coefficient
  of the plant overlaps the month. `hoursAboveNominalPower` MUST list every hour whose surplus is above the plant's
  nominal power times one hour.
- **Rationale:** The plant's own share is never claimed, a month without coefficients has nothing to claim, and an
  hour above what the plant can produce points at bad data.
- **Source:** #629

### SCL-012 — Warnings never block

- **Status:** Active
- **Rule:** No warning MUST prevent the response. A warning list MUST be empty, never null, when it is not raised.
  Supplies MUST be ordered by code, and days and hours chronologically.
- **Rationale:** The admin decides; the application informs.
- **Source:** #629

### SCL-013 — Readable by the community's admins, with the supplies' CUPS

- **Status:** Active
- **Rule:** The claim MUST be readable only by a community admin of the plant's community. It MUST answer 403 to a
  member of that community and 404 to anyone else. Unlike PEM-004, its warnings MUST name the supplies concerned with
  their CUPS.
- **Rationale:** The admin manages those supplies and has to identify them to the retailer.
- **Source:** #629, PEM-004

## Impossible states

- A missing hour reported as 0 — prevented by SCL-002.
- A month's income without a month's surplus — prevented by SCL-006.
- A supply listed both without data and with gaps — prevented by SCL-009: a supply with gaps has records.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A supply holding coefficients in two plants counts whole in each plant's claim. | Splitting a supply's surplus across plants is out of scope. | A claim splits a supply's surplus across plants. | #629 |
