# Spec — Sharing agreements

| | |
| --- | --- |
| **Prefix** | `SHA` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Sharing agreements`; operations `updateSharingAgreement`, `activatePartitionCoefficients`, `deactivatePartitionCoefficients`, `closePartitionCoefficients`, `reopenPartitionCoefficients`, `getSharingAgreementPartitionCoefficients`, `setPartitionCoefficientDates`, `previewPartitionCoefficientDates`, `replacePartitionCoefficients`, `uploadSharingAgreementFile` |
| **Related ADRs** | ADR-0001 (`docs/architecture/adr/0001-deferred-exclusion-constraint-for-coefficient-periods.md`), ADR-0003 §11 (lock order) |
| **Last reviewed against code** | #601 |

## Purpose

A sharing agreement is the community's record of how a plant's production is shared among supplies: the partition
coefficients authored together, the evidence they came from, and the distributor file sent to the distribution
company. This spec covers only the rules #580, #581, #582, #593, #583, #591 and #601 touched: the day the distributor
file was sent, the periods during which a coefficient applies, how setting a coefficient's start places it in its
supply's timeline, how an agreement's first activation closes the coefficients of the supplies that leave the
distribution, how an agreement's dates are set per supply in one request, with a preview of every effect, how each
coefficient of an agreement is reported to end, that a supply holds one coefficient per agreement, and how two
replacements of an agreement's coefficients are ordered.

## Glossary

| Term | Meaning |
| --- | --- |
| Sharing agreement | An authoring event over a plant's coefficient timeline: the coefficients set together, with their evidence. |
| Partition coefficient | The share of a plant's production a sharing agreement assigns to a supply, for a period. |
| Distributor file | The file of coefficients the community sends to the distribution company, which applies them (RD 244/2019). |
| Application time zone | The single time zone in which Conluz turns local dates into instants and decides what "today" is. |
| Timeline | The activated coefficients of one supply in one plant, ordered by validFrom. Pending coefficients are not part of it. |
| Authored close | A validTo set by closing a coefficient, as opposed to one the cascade wrote when the next coefficient was placed. Inferred from adjacency: a validTo where the next coefficient starts counts as cascade-derived. |
| First activation | An activation of an agreement when none of its coefficients had a validFrom before the request. After a full deactivation, the next activation is a first activation again. |
| Leaving supply | A supply of the plant that has no coefficient, pending or not, in the agreement being activated, and has a coefficient in force on its date. |
| In force at D | A coefficient with validFrom before D and validTo null or after D. |
| Agreement start | The earliest validFrom among an agreement's coefficients; an agreement none of whose coefficients is activated has none. |
| Effects | Everything a date write changes: each period, each agreement's status and the community start date. |
| Community start date | The earliest validFrom among the coefficients of all the plants of a community (payback's start). |
| Registered order | The agreements of a plant ordered by their distributor file's sent date, else the earliest stored start of their coefficients as a local day in the application time zone; then by creation instant; then by id. Agreements with neither date come last. The same order as observed sharing (OBS-003). |
| End state | How a coefficient's coverage ends, as `getSharingAgreementPartitionCoefficients` reports it: `OPEN`, `OPEN_ORPHAN`, `PENDING_SUCCESSION`, `DERIVED` or `CLOSED`. |

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
- **Rule:** Setting a coefficient's validFrom (`activatePartitionCoefficients` or `setPartitionCoefficientDates`, for
  a pending or an active coefficient) MUST place it chronologically within the timeline of its (plant, supply), whatever order the
  agreements are activated in. It ends at the validFrom of the next coefficient of that timeline, or stays open if
  there is none. The previous coefficient's validTo is moved back to the new validFrom only when it is open or later
  than it; a validTo on or before it is kept, and so is the gap after it. Placing a coefficient never extends another
  period; the join of a correction is governed by SHA-004. Pending coefficients are not part of the timeline.
- **Rationale:** Allows reconstructing history in any order without overlaps: an older agreement can be activated
  while a newer one is open, bounded by its neighbours on both sides. Never extending a period keeps authored
  closes: a close is the community's record that a supply left, which a later activation must not erase.
- **Source:** #582, #583 (`setPartitionCoefficientDates`)

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
  `deactivatePartitionCoefficients`, `closePartitionCoefficients`, `reopenPartitionCoefficients`,
  `setPartitionCoefficientDates`) on the same plant MUST run one after another, each reading the timeline the one
  before it left. `previewPartitionCoefficientDates` writes nothing and is not serialised.
- **Rationale:** The exclusion constraint does not protect non-overlapping cascades: two writes can each leave a
  timeline without overlaps and still undo each other's cascade. Lock order: ADR-0003 §11.
- **Source:** #582, #583 (`setPartitionCoefficientDates`)

### SHA-008 — An applied open coefficient is not shown as ended by a row placed before it

- **Status:** Active
- **Rule:** When an agreement's coefficients are read (`getSharingAgreementPartitionCoefficients`), an applied open
  coefficient MUST NOT be reported as ended (`DERIVED`) by a coefficient of any other agreement that starts before it.
- **Rationale:** Since SHA-003 an agreement created later may hold an earlier period, when history is reconstructed;
  that coefficient does not succeed the open one, which is still in force.
- **Source:** #582; widened from later-created agreements to any agreement by #591, which no longer reads creation
  order (SHA-016)

### SHA-009 — An agreement's first activation closes the coefficients of the supplies absent from it

- **Status:** Active
- **Rule:** The first activation of an agreement at D (`activatePartitionCoefficients`) MUST close at D, in the same
  transaction, the coefficient in force at D of every supply of the plant that has no coefficient in that agreement.
  A supply holding a coefficient of the agreement, even a pending one outside the batch, is not leaving. Every
  agreement owning a closed coefficient MUST have its status recomputed (SHA-006), and the response MUST report each
  closure (`leavingClosures`: supply, coefficient, agreement, new validTo). Any other activation of the agreement (a
  later batch, a correction) MUST NOT close anything. If any item of the batch fails validation, the whole
  activation MUST be refused and no closure written.
- **Rationale:** A supply that leaves the distribution between two agreements has no row in the new one, so without
  this its coefficient stays open and production keeps being attributed to it. Only the first activation decides,
  because later batches and corrections must not re-close coefficients an admin has reopened; per-supply leaving
  dates are a separate operation (#583). A closure this rule wrote at the wrong date can be corrected, shortened or
  extended, with no reopen, by listing the supply in `setPartitionCoefficientDates` (SHA-013).
- **Source:** #593

### SHA-010 — A first activation's leaving closure only shortens a period

- **Status:** Active
- **Rule:** A leaving closure written by a first activation (SHA-009, and the default closures of SHA-013) MUST only
  shorten a period: a coefficient that ended on or before D is not
  touched, one that ends after D is shortened to D, and a coefficient that starts on or after D is never touched.
- **Rationale:** Consistent with chronological placement (SHA-003): when an older agreement is activated while history
  is reconstructed, the rows of a newer agreement start after D and are not in force there. A closing date the
  admin sets for a listed supply (SHA-013) may extend the period instead: the admin states when the supply left, so
  there is no authored close of theirs to protect.
- **Source:** #593; narrowed to a first activation's closures by #583

### SHA-011 — Leaving closures are ordinary authored closes

- **Status:** Active
- **Rule:** A leaving closure MUST be stored as an ordinary close of the coefficient. Deactivating the agreement whose
  activation closed it (`deactivatePartitionCoefficients`) MUST NOT reopen it; an admin reopens it with
  `reopenPartitionCoefficients`.
- **Rationale:** Reopening it automatically would require storing which activation caused each closure, a schema
  change; the reopen operation already exists.
- **Source:** #593

### SHA-012 — An agreement's coefficients may be dated individually in one request

- **Status:** Active
- **Rule:** `setPartitionCoefficientDates` MAY give each coefficient of one agreement its own validFrom. Each MUST be
  placed per SHA-003 to SHA-005, in ascending validFrom order and then by coefficient id, each on the timeline as the
  previous ones left it. A pending coefficient is activated and an active one corrected; one whose validFrom is
  already the requested one is not touched. The agreement MUST be PUBLISHED or SUPERSEDED.
- **Rationale:** The distributor applies an agreement on different days for different supplies; one call per
  distinct date left the agreement half-dated between calls.
- **Source:** #583

### SHA-013 — A leaving supply is closed at its own date, shortened or extended

- **Status:** Active
- **Rule:** For each supply `setPartitionCoefficientDates` lists with a closing date, the supply MUST have no
  coefficient, pending or not, in the agreement. Its closed coefficient is its activated coefficient with the greatest
  validFrom before the agreement start, once the request's coefficients are placed; its validTo becomes the closing
  date. That MAY shorten or extend the period, up to the supply's next start; a closing date after it MUST be
  refused as an overlap. On the agreement's first activation (SHA-009), every leaving supply the request does not
  list MUST be closed at the earliest validFrom of the request, as SHA-009 does, and reported as a default closure;
  any other request MUST NOT close a supply it does not list. Every closure is an ordinary close (SHA-011).
- **Rationale:** Leaving dates differ per supply, and closures SHA-009 wrote at the agreement's date may need
  correcting once the real leaving dates are known. The closed coefficient is anchored on the agreement start, the
  row the supply had before this agreement, so a closing date later than a newer row of the supply is refused rather
  than closing that newer row.
- **Source:** #583

### SHA-014 — The preview returns exactly the effects the write performs, and writes nothing

- **Status:** Active
- **Rule:** `previewPartitionCoefficientDates` MUST compute, for the same body, the effects `setPartitionCoefficientDates`
  performs on the timeline as it stands: each requested coefficient's period before and after, each other coefficient
  whose end moves, each leaving closure with whether it was requested or a default, the status of every agreement
  owning a changed coefficient before and after (SHA-006), and the community start date before and after. It MUST
  write nothing and take no lock (SHA-007); the write validates the request again. The write MUST return the same
  effects, as written. Coefficients whose period does not change are not reported.
- **Rationale:** The admin decides with full information: a date write can close or reopen rows of other agreements,
  supersede them and move the community's payback start.
- **Source:** #583

### SHA-015 — A date request is atomic, with per-row errors

- **Status:** Active
- **Rule:** If any row of a `setPartitionCoefficientDates` or `previewPartitionCoefficientDates` request is invalid,
  the whole request MUST be refused with 409 and every invalid row reported, and nothing written. Invalid rows
  include: a date in the future; a start equal to another start of the supply, or an overlap; a coefficient not in
  the agreement; a correction on or after the coefficient's own close (SHA-004); a listed supply holding a
  coefficient in the agreement; a listed supply with no coefficient to close; a closing date not after the closed
  coefficient's start. A request whose lists are both empty, or that lists a coefficient or a supply twice, MUST be
  refused with 400.
- **Rationale:** A half-applied request leaves timelines no one asked for; reporting every invalid row lets the
  admin fix them all at once. The preview answers the same refusal so that what it accepts is what the write accepts.
- **Source:** #583

### SHA-016 — An applied open coefficient's end state follows validity, not creation order

- **Status:** Active
- **Rule:** When an agreement A's coefficients are read (`getSharingAgreementPartitionCoefficients`), an applied
  coefficient C with no stored validTo MUST be reported:
  - `DERIVED`, with that start as its end date, when an activated coefficient of the same supply in the plant starts
    after C;
  - otherwise `OPEN_ORPHAN` when another non-DRAFT agreement of the plant has an activated coefficient starting after
    C and holds no coefficient of C's supply;
  - otherwise `PENDING_SUCCESSION` when an agreement after A in registered order holds a pending coefficient of C's
    supply;
  - otherwise `OPEN`.

  DRAFT agreements other than A are ignored. The creation order of the agreements MUST NOT change any end state.
- **Rationale:** Since SHA-003 an agreement created later may hold an earlier period, when history is reconstructed
  under an agreement that is already open, so only validity says what comes after an applied coefficient. An
  agreement whose activated coefficients all start before C is not after it. A pending coefficient has no validity, so
  whether it succeeds C is decided by registered order, which until a sent date is recorded places an undated pending
  agreement last.
- **Source:** #591

### SHA-017 — A pending coefficient's end state follows registered order

- **Status:** Active
- **Rule:** When an agreement A's coefficients are read, a pending coefficient C (no validFrom) MUST be reported by
  the next non-DRAFT agreement after A in registered order:
  - `OPEN` when there is none;
  - `OPEN_ORPHAN` when it holds no coefficient of C's supply;
  - `DERIVED`, with that start as its end date, when it holds an activated coefficient of C's supply;
  - `PENDING_SUCCESSION` when it holds only a pending one.

  The creation order of the agreements MUST NOT change any end state.
- **Rationale:** A pending coefficient has no period to compare, so the only order between it and other agreements is
  the one they were registered in; creation order no longer says it once history is imported after the current
  agreement.
- **Source:** #591

### SHA-018 — A supply holds at most one coefficient per sharing agreement

- **Status:** Active
- **Rule:** A supply MUST hold at most one coefficient, pending or not, in a sharing agreement, whatever wrote it. The
  database enforces it with a unique constraint on (sharing_agreement_id, supply_id). A write that would give a supply
  a second coefficient in an agreement MUST be refused with 400 `SHARING_AGREEMENT_DUPLICATE_SUPPLY` and nothing
  written, whether the request lists the supply twice or the constraint rejects the row.
- **Rationale:** A business rule that placement order (#583), observed sharing (#579) and the per-row observed β
  (#597) depend on. Until #601 only materialisation checked it, so data written another way, such as the migration
  that attached every pre-existing coefficient to one synthetic agreement per plant, could break it unnoticed. The
  migration that adds the constraint halts on existing duplicates instead of fixing them, because which row of a supply
  to keep is the community's decision.
- **Source:** #601

### SHA-019 — The materialisation of an agreement's coefficients is serialised per agreement

- **Status:** Active
- **Rule:** Two replacements of the coefficients of the same agreement (`replacePartitionCoefficients`,
  `uploadSharingAgreementFile`) MUST run one after another: the second waits for the first, then replaces the first
  one's set, and only if the agreement is still DRAFT; otherwise it is refused as not DRAFT and nothing written.
  Replacements of different agreements MUST NOT wait for each other.
- **Rationale:** Each replacement deletes the agreement's set and inserts its own. Run at the same time, the second one's
  delete does not see the first one's uncommitted rows, so both sets would survive, or, with SHA-018, the second
  request would fail. The status is checked again once the agreement is locked, because a publish may commit while the
  replacement waits. Lock order: ADR-0003 §11.
- **Source:** #601

## Impossible states

- A distributor file sent date in the future, as of the day it was written — prevented by SHA-001.
- Two coefficients of one supply that succeed each other both applying on the day of the change — prevented by SHA-002
  and the `no_overlapping_coefficients` exclusion constraint (ADR-0001).
- Two coefficients of one supply in one plant that start on the same day — prevented by SHA-005.
- A placement that extends the period of another coefficient — prevented by SHA-003 (a correction's join, SHA-004,
  is the one write that extends a period, and only one that was cascade-derived).
- Two date writes on one plant each undoing the other's cascade — prevented by SHA-007.
- A leaving closure written by a first activation that extends a period, or touches a coefficient starting on or
  after the activation date — prevented by SHA-010.
- A requested leaving closure that extends a period past the supply's next start — prevented by SHA-013.
- A date request applied in part — prevented by SHA-015.
- An applied open coefficient with an activated coefficient of the same supply starting after it — prevented by
  SHA-003 and the `no_overlapping_coefficients` exclusion constraint; reported `DERIVED` if found (SHA-016).
- Two coefficients of one supply in one agreement — prevented by SHA-018 and its unique constraint.
- Two replacements of one agreement's coefficients interleaving, or a replacement writing into an agreement that is no
  longer DRAFT — prevented by SHA-019.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| An authored close that exactly equals the next coefficient's validFrom is joined or replaced like a cascade-derived one when a coefficient is corrected (SHA-004) or deactivated. | Nothing records who wrote a validTo; adjacency is the only signal without a schema change. | A coefficient stores whether its validTo was authored. | #582 |
