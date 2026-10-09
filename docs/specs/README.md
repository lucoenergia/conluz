# Specs

Living specifications: the **current** behaviour of the system, written as normative rules.
Issues describe a change (the delta); specs describe the consolidated state after it.

- Domain rules live in `lucoenergia/conluz` → `docs/specs/<capability>.md`
- UI rules live in `lucoenergia/conluz-web` → `docs/specs/<screen-or-flow>.md`

## What a spec is not

- **Not the API contract.** The OpenAPI snapshot (`src/test/resources/openapi/api-docs.json`, see
  `AGENTS.md` → "The OpenAPI snapshot") is authoritative for fields, types and status codes.
  Specs reference operations by `operationId`; they never restate schemas.
- **Not an ADR.** ADRs (`docs/architecture/adr/`) record architectural decisions. A rule may link to the ADR that motivates it.
- **Not a changelog.** History lives in git and in the issues referenced by each rule.
- **Not a backlog.** Open questions and future work belong in GitHub issues.

## Rule IDs

- Format: `<PREFIX>-<NNN>` for domain rules (`SUP-001`), `UI-<PREFIX>-<NNN>` for UI rules (`UI-SUP-001`).
- One prefix per capability, registered in the table below.
- IDs are **never reused and never renumbered**. A retired rule stays in the file with status
  `Removed` so its ID cannot be taken again.
- Tests reference the ID in their name or display name, e.g.
  `@DisplayName("SUP-001 supply cannot be moved to another community")` or
  `test('UI-SUP-002 blocking reason is rendered as text', ...)`.
  Java method names cannot contain `-`, so JUnit tests carry the ID in `@DisplayName`.
- Traceability is checked with `rg`, never by listing test names inside the spec:
  `rg -n "SUP-001"`.

## Rule statuses

| Status | Meaning |
| --- | --- |
| `Active` | Enforced by the current code |
| `Deprecated` | Still enforced, scheduled for removal (link the issue) |
| `Removed` | No longer enforced; kept as a tombstone (link the issue that removed it) |

## Maintenance

- Every PR that changes behaviour updates the affected spec in a separate commit, following the
  repository commit format (`[conluz-XXX] …`).
- Specs are created lazily: a capability gets its spec the first time an issue touches it.
  No bulk backfill.

## Workflow (spec-driven development)

1. **Issue.** Describes the change (the delta) and fills in *Spec impact*: which rules are
   added, changed or removed, with their rationale. New rules use placeholders (`NEW-1`).
2. **Plan.** The agent reads the affected spec during reconnaissance and confirms it matches
   the code. If spec and code disagree, it stops and reports; it never fixes either side silently.
3. **Implementation.** Code and tests. Each added or changed rule is covered by at least one test
   whose name starts with the rule ID.
4. **Spec update.** In the same PR, in a separate commit: rules added, changed or tombstoned,
   with Rationale and Source. New IDs take the next free number.
5. **PR.** The *Spec changes* section lists each rule touched and the tests that cover it.
6. **Review.** The maintainer checks the spec delta against the issue's *Spec impact*.
7. **Merge.** The spec on `main` is the current truth; the issue can close without losing the why.

## Terminology

In this documentation, **spec** means a specification document under `docs/specs/`. Test files
are always called **tests**, never specs.

## Prefix registry

| Prefix | Capability | File |
| --- | --- | --- |
| `ENM` | Membership energy metrics | [`membership-energy-metrics.md`](membership-energy-metrics.md) |
