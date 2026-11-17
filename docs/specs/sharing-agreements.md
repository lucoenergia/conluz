# Spec — Sharing agreements

| | |
| --- | --- |
| **Prefix** | `SHA` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Sharing agreements`; operations `updateSharingAgreement`, `activatePartitionCoefficients`, `deactivatePartitionCoefficients`, `closePartitionCoefficients`, `reopenPartitionCoefficients`, `getSharingAgreementPartitionCoefficients` |
| **Related ADRs** | ADR-0001 (`docs/architecture/adr/0001-deferred-exclusion-constraint-for-coefficient-periods.md`), ADR-0003 §11 (lock order) |
| **Last reviewed against code** | #582 |

## Purpose

A sharing agreement is the community's record of how a plant's production is shared among supplies: the partition
coefficients authored together, the evidence they came from, and the distributor file sent to the distribution
company. This spec covers only the rules #580, #581 and #582 touched: the day the distributor file was sent, the
periods during which a coefficient applies, and how setting a coefficient's start places it in its supply's timeline.

## Glossary

| Term | Meaning |
| --- | --- |
| Sharing agreement | An authoring event over a plant's coefficient timeline: the coefficients set together, with their evidence. |
| Partition coefficient | The share of a plant's production a sharing agreement assigns to a supply, for a period. |
| Distributor file | The file of coefficients the community sends to the distribution company, which applies them (RD 244/2019). |
| Application time zone | The single time zone in which Conluz turns local dates into instants and decides what "today" is. |
| Timeline | The activated coefficients of one supply in one plant, ordered by validFrom. Pending coefficients are not part of it. |
| Authored close | A validTo set by closing a coefficient, as opposed to one the cascade wrote when the next coefficient was placed. Inferred from adjacency: a validTo where the next coefficient starts counts as cascade-derived. |

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

### SHA-003 — Setting a coefficient's validFrom places it chronologically

- **Status:** Active
- **Rule:** Setting a coefficient's validFrom (`activatePartitionCoefficients`, for a pending or an active
  coefficient) MUST place it chronologically within the timeline of its (plant, supply), whatever order the
  agreements are activated in. It ends at the validFrom of the next coefficient of that timeline, or stays open if
  there is none. The previous coefficient's validTo is moved back to the new validFrom only when it is open or later
  than it; a validTo on or before it is kept, and so is the gap after it. Placing a coefficient never extends a
  period. Pending coefficients are not part of the timeline.
- **Rationale:** Allows reconstructing history in any order without overlaps: an older agreement can be activated
  while a newer one is open, bounded by its neighbours on both sides. Never extending a period keeps authored
  closes: a close is the community's record that a supply left, which a later activation must not erase.
- **Source:** #582

### SHA-004 — Correcting an active coefficient first joins its former neighbours

- **Status:** Active
- **Rule:** Correcting an active coefficient's validFrom MUST first join its former neighbours, as a deactivation
  does: the coefficient whose validTo equals the corrected one's former validFrom takes over its former validTo. It
  is then placed at the new date by SHA-003. A validTo the corrected coefficient carries itself and that was not
  cascade-derived (an authored close) MUST be kept, shortened to the next coefficient's validFrom if that comes
  earlier; a new validFrom on or after it MUST be refused with 409 and nothing changed. A predecessor separated from
  the corrected coefficient by a gap is not joined.
- **Rationale:** Prevents gaps and stale cascades when a coefficient moves. Never extending a period keeps authored
  closes, the corrected coefficient's own included. Whether a validTo was cascade-derived is inferred from adjacency
  only (the next coefficient starts exactly there), because nothing records who wrote it: an authored close that
  happens to equal the next coefficient's validFrom is indistinguishable from a cascade-derived one, and is joined
  or replaced like one. This is a known limitation; telling them apart needs a stored flag, a schema change outside
  #582.
- **Source:** #582

### SHA-005 — A validFrom equal to another start, or in the future, is refused

- **Status:** Active
- **Rule:** A validFrom equal to the validFrom of another coefficient of the same (plant, supply), or after today in
  the application time zone, MUST be refused with 409, and nothing in the batch changed. An equal start is reported
  as an overlap with that coefficient.
- **Rationale:** Zero-length and future periods are not meaningful. Two coefficients that start on the same day
  would both apply on it.
- **Source:** #582

### SHA-006 — Every agreement a date write touches has its status recomputed

- **Status:** Active
- **Rule:** After a coefficient date write, the status (PUBLISHED or SUPERSEDED) of every agreement owning a
  coefficient whose period the write changed MUST be recomputed from its coefficients, in the same transaction.
- **Rationale:** Keeps SUPERSEDED a computed invariant: a placement can close a coefficient of an older agreement, or
  reopen one through a join, in an agreement other than the one being activated.
- **Source:** #582

### SHA-007 — Coefficient date writes are serialised per plant

- **Status:** Active
- **Rule:** Writes of coefficient validity dates (`activatePartitionCoefficients`,
  `deactivatePartitionCoefficients`, `closePartitionCoefficients`, `reopenPartitionCoefficients`) on the same plant
  MUST run one after another, each reading the timeline the one before it left.
- **Rationale:** The exclusion constraint does not protect non-overlapping cascades: two writes can each leave a
  timeline without overlaps and still undo each other's cascade. Lock order: ADR-0003 §11.
- **Source:** #582

### SHA-008 — An applied open coefficient is not shown as ended by a row placed before it

- **Status:** Active
- **Rule:** When an agreement's coefficients are read (`getSharingAgreementPartitionCoefficients`), an applied open coefficient MUST NOT be reported as ended
  (`DERIVED`) by a coefficient of a later-created agreement that starts before it; it is reported `OPEN`.
- **Rationale:** Since SHA-003 an agreement created later may hold an earlier period, when history is reconstructed;
  that coefficient does not succeed the open one, which is still in force.
- **Source:** #582

## Impossible states

- A distributor file sent date in the future, as of the day it was written — prevented by SHA-001.
- Two coefficients of one supply that succeed each other both applying on the day of the change — prevented by SHA-002
  and the `no_overlapping_coefficients` exclusion constraint (ADR-0001).
- Two coefficients of one supply in one plant that start on the same day — prevented by SHA-005.
- A placement that extends the period of another coefficient — prevented by SHA-003 (a correction's join, SHA-004,
  is the one write that extends a period, and only one that was cascade-derived).
- Two date writes on one plant each undoing the other's cascade — prevented by SHA-007.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| An authored close that exactly equals the next coefficient's validFrom is joined or replaced like a cascade-derived one when a coefficient is corrected (SHA-004) or deactivated. | Nothing records who wrote a validTo; adjacency is the only signal without a schema change. | A coefficient stores whether its validTo was authored. | #582 |
