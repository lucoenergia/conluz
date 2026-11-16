# Spec — Sharing agreements

| | |
| --- | --- |
| **Prefix** | `SHA` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Sharing agreements`; operations `updateSharingAgreement`, `activatePartitionCoefficients`, `closePartitionCoefficients` |
| **Related ADRs** | ADR-0001 (`docs/architecture/adr/0001-deferred-exclusion-constraint-for-coefficient-periods.md`) |
| **Last reviewed against code** | #580 |

## Purpose

A sharing agreement is the community's record of how a plant's production is shared among supplies: the partition
coefficients authored together, the evidence they came from, and the distributor file sent to the distribution
company. This spec covers only the rules #580 and #581 touched: the day the distributor file was sent, and the
periods during which a coefficient applies.

## Glossary

| Term | Meaning |
| --- | --- |
| Sharing agreement | An authoring event over a plant's coefficient timeline: the coefficients set together, with their evidence. |
| Partition coefficient | The share of a plant's production a sharing agreement assigns to a supply, for a period. |
| Distributor file | The file of coefficients the community sends to the distribution company, which applies them (RD 244/2019). |
| Application time zone | The single time zone in which Conluz turns local dates into instants and decides what "today" is. |

## Rules

### SHA-001 — An agreement may record the day its distributor file was sent

- **Status:** Active
- **Rule:** A sharing agreement MAY record the local date its distributor file was sent to the distributor. The date
  is optional and MUST NOT be after today in the application time zone; a later date MUST be refused with 400 and
  nothing changed. It MAY be set, changed or cleared in any agreement status (DRAFT, PUBLISHED, SUPERSEDED), and
  is NEVER validated against the dates of the agreement's coefficients: a sent date later than the day a coefficient
  started to apply MUST be accepted. It is written by `updateSharingAgreement`, which replaces every updatable field
  of the agreement; so, the same as the notes, a request that omits the date or sends null clears it. That follows
  from `updateSharingAgreement` being a full replacement, not from a rule of this field.
- **Rationale:** Lower bound for application; enables "pending application" and the applied-before-sent anomaly.
  The date is not checked against coefficient dates because either side may be the wrong one: the observed sharing
  analysis (#579) reports a coefficient applied before its file was sent as an anomaly, instead of the write being
  refused.
- **Source:** #580

### SHA-002 — Coefficient periods are half-open

- **Status:** Active
- **Rule:** Coefficient periods MUST be half-open [validFrom, validTo). validTo and endDate MUST denote the first day
  the coefficient no longer applies, and validFrom the first day it applies; all three are instants at local midnight
  in the application time zone. appliedOn and closedOn are local dates, stored as validFrom and validTo respectively.
  So activating a coefficient on day X closes the one it succeeds at exactly X, and the two never both apply on X.
- **Rationale:** Clients must not guess whether end dates are inclusive; the web already displays them as exclusive.
- **Source:** #581

## Impossible states

- A distributor file sent date in the future, as of the day it was written — prevented by SHA-001.
- Two coefficients of one supply that succeed each other both applying on the day of the change — prevented by SHA-002
  and the `no_overlapping_coefficients` exclusion constraint (ADR-0001).

## Accepted exceptions

None.
