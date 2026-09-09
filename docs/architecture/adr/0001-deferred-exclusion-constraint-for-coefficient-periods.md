# ADR-0003 — Defer the coefficient overlap constraint and re-check it explicitly

- **Status:** Accepted
- **Date:** 2026-09-09
- **Deciders:** Víctor Cañizares
- **Applies to:** `lucoenergia/conluz` — `supply_partition_coefficient` and the coefficient
  activation cascade

## Context

`supply_partition_coefficient` holds one row per `(plant, supply, validity period)`. Overlapping
periods for the same supply would mean the same production attributed twice, so the table carries a
GiST exclusion constraint, `no_overlapping_coefficients`, over
`(plant_id, supply_id, tstzrange(valid_from, valid_to, '[)'))` where `valid_from IS NOT NULL`.

Activating a coefficient is a **cascade**: within one transaction the service opens the newly
activated row and closes the previously open row for the same supply, writing its `valid_to`. This
cascade is the mechanism that prevents overlapping distributions in the first place; the constraint
exists to catch the cases the mechanism does not.

The transaction's *final* state is always consistent. Its *intermediate* state is not: between the
two `UPDATE`s, two rows are open-ended for the same supply.

Postgres checks a non-deferrable constraint immediately after each statement. Hibernate flushes
pending updates of the same entity type in **persistence-context load order**, not in the order the
service queued them. The activation service loads the successor first and the predecessor second, so
the flush emits "open successor" before "close predecessor" — and the constraint rejects the first
`UPDATE` on a transaction that would have committed cleanly.

The result was an unhandled 500 on `POST .../partition-coefficients/activate` whenever the activated
coefficient had an open predecessor, which is the ordinary case in a rollout.

Two details made this worse than a routine ordering bug:

- The existing integration test passed by accident. Its setup persisted the predecessor before the
  successor, which happened to put the predecessor first in the persistence context too. It never
  reproduced the production load order and could not have caught this.
- The failure is invisible in code review. Nothing in the service reads wrongly; the defect lives in
  the interaction between an ORM's internal flush ordering and a database check point.

## Decision

Two changes, and both are required. Either one alone leaves the system worse than the other.

### 1. The constraint becomes `DEFERRABLE INITIALLY DEFERRED`

Postgres does not support `ALTER CONSTRAINT` on exclusion constraints, so this is a drop and re-add,
identical except for the deferrability clause.

Correctness now depends on the transaction's final state rather than on Hibernate's flush ordering.
That is where a temporal-range invariant belongs: the cascade's contract is "no overlaps once this
transaction completes", not "no overlaps between any two statements".

### 2. The cascade re-checks the constraint explicitly, before returning

At the end of the cascade method, still inside the transaction:

1. `flush()`, so the pending `UPDATE`s reach the database.
2. Set `no_overlapping_coefficients` to `IMMEDIATE` for the current transaction, forcing Postgres to
   resolve the deferred checks at that point.
3. Catch the resulting `DataIntegrityViolationException`, identify the constraint **by name** — never
   by parsing the message text — and rethrow a typed domain exception mapped to a 409.

Without this, deferring alone moves the failure to commit, *outside* the `@Transactional` method
body, where no service code can catch it and no domain context survives to explain it. The endpoint
would keep returning a 500, just from a different place.

A handler-level mapping of the commit-time failure remains as a net for any other path that writes to
this table. It is the net, not the mechanism.

## What a genuine overlap means after this change

The date validations already reject the ordered user errors
(`SHARING_AGREEMENT_ACTIVATION_DATE_NOT_AFTER_PREDECESSOR`,
`SHARING_AGREEMENT_ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR`, `SHARING_AGREEMENT_DATE_IN_FUTURE`), and
the cascade closes the predecessor. So a surviving overlap is **not** a user mistake. It is one of:

- **Concurrency** — two activations on the same supply, each validating against a state the other is
  changing. A legitimate conflict.
- **Pre-existing inconsistent rows**, written before the constraint existed or by a path that
  bypassed validation.
- **A cascade defect**, which this check now surfaces loudly instead of silently persisting bad data.

The error message therefore states that the stored state changed and the request should be reloaded
and retried. It must not imply the user entered a wrong date: that would be false in all three cases.

## Alternatives considered

**Reorder the two loads in the service** so the predecessor is fetched first. One line, and it works.
Rejected: it makes system correctness depend on an undocumented ORM implementation detail that no
test can pin and that the next refactor would silently break. The accidentally-passing integration
test is direct evidence of how fragile that coupling is.

**Collapse the cascade into a single statement** so no intermediate state exists. Rejected: it would
push domain logic into SQL, and the cascade will need to grow (deactivate splices the chain, close
and reopen write their own boundaries). The problem would return with the next path.

**Drop the constraint and rely on the service.** Rejected outright. This is the invariant that
prevents double attribution of production, on data that reaches member billing. Application-level
enforcement cannot survive concurrency, and there is no second line of defence behind it.

**Keep it non-deferrable and catch the violation with a retry.** Rejected: retrying does not help,
because the ordering is deterministic per request shape, not a race.

## Consequences

**Positive**

- The activation cascade works regardless of flush order, and so will `deactivate`, `close` and
  `reopen`, which share the same transient-overlap shape.
- Failures are reported inside the service, with domain context, as a typed 409 rather than a 500.
- The invariant remains enforced by the database, which is the only place that survives concurrency.

**Negative**

- **Any future temporal cascade must follow this pattern.** Deferring without the explicit re-check
  produces an unhandleable commit-time failure. The two changes are one decision, not two.
- The constraint no longer catches a mistake at the statement that caused it, so a stack trace points
  at the re-check rather than at the offending write. The re-check's message must carry enough
  context to locate the cause.
- Tests that relied on `flush()` triggering the check must set the constraint to `IMMEDIATE`
  explicitly. Those tests now exercise a mode production does not use, so at least one test must
  verify rejection **at commit** under the real deferred configuration — otherwise a mistyped
  deferrability clause would pass the whole suite.

**Operational**

- The drop-and-re-add takes an `ACCESS EXCLUSIVE` lock and rebuilds the GiST index. Negligible at
  current data volume; anyone applying this to a larger database should know before they run it.
- The `ADD` fails outright if any overlapping rows already exist. On a database with pre-existing
  inconsistency, the migration is the thing that finds out.

## Revisit if

- Postgres gains `ALTER CONSTRAINT` support for exclusion constraints, making the drop-and-re-add
  unnecessary for future changes of this kind.
- The activation cascade stops writing more than one row per supply per transaction, which would
  remove the intermediate state and the reason to defer.
- A genuine overlap conflict is observed in production, since that would mean either a concurrency
  pattern worth handling explicitly or a cascade defect worth fixing at source.
- The time-series layer migrates to TimescaleDB (see the pending ADR on that), in case it changes how
  these ranges are stored.

## References

- Migration:
  `src/main/resources/db/liquibase/changelogs/make_supply_partition_coefficient_no_overlapping_constraint_deferrable.xml`
- Original constraint:
  `src/main/resources/db/liquibase/changelogs/add_supply_partition_coefficient_no_overlapping_exclusion_constraint.xml`
- Cascade: `CoefficientActivationServiceImpl`
