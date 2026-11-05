# Spec — API contract

| | |
| --- | --- |
| **Prefix** | `API` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot (`src/main/resources/api/docs/api-docs.json`) → every request body schema |
| **Related ADRs** | — |
| **Last reviewed against code** | #549 |

## Purpose

Rules that hold for the published contract as a whole rather than for one capability: what the generated
OpenAPI document must say about the requests every endpoint accepts. The rules of each capability decide
the bounds; this spec decides how they reach the contract.

## Glossary

| Term | Meaning |
| --- | --- |
| Numeric bound | A limit on the value of a number in a request, enforced by bean validation (`@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero`, `@Digits`) |
| Side | Whether a bound limits a value from below (minimum) or from above (maximum) |

## Rules

### API-001 — Every numeric bound of a request body is published, exclusive bounds as exclusive, strictest per side

- **Status:** Active
- **Rule:** Every numeric bound enforced by bean validation on request body properties MUST be published in the
  contract, exclusive bounds as exclusive, and only the strictest bound per side. An exclusive and an inclusive
  bound with the same value MUST be published as the exclusive one. Publishing a bound MUST NOT change how a
  request is validated, nor any other part of the property's schema.
- **Rationale:** Clients are generated from the contract (`conluz-web` with Orval, mock factories included). A bound
  the contract omits or understates lets them build values that the server only rejects with a 400 at runtime.
  One mechanism, rather than a bound restated per field, keeps every future constraint published without anyone
  having to remember it. Operation parameters are left out: the mechanism only sees model properties.
- **Source:** #549

## Impossible states

None.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Numeric constraints on operation parameters (`@RequestParam`, `@PathVariable`, `@RequestHeader`) are not covered by the API-001 mechanism | Whether springdoc publishes numeric constraints on parameters correctly has not been verified; no operation parameter carries one today | An operation parameter gains a numeric constraint | #549 |
