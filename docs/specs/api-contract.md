# Spec — API contract

| | |
| --- | --- |
| **Prefix** | `API` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot (`src/main/resources/api/docs/api-docs.json`) → every request body schema; operations `getInstantProduction`, `getHourlyProduction`, `getDailyProduction`, `getMonthlyProduction`, `getYearlyProduction`, `getDatadisHourlyProduction`, `getDatadisDailyProduction`, `getDatadisMonthlyProduction`, `getDatadisYearlyProduction`, `getSupplyHourlyProduction`, `getSupplyDailyProduction`, `getSupplyMonthlyProduction`, `createSupply`, `updateSupply`, `createPlant`, `updatePlant`, `createCommunity`, `updateCommunity`, `registerMember`, `updateMemberRecord`, `updateProfile`, `init`, `importSupplies`, `importMembers` |
| **Related ADRs** | — |
| **Last reviewed against code** | #574 |

## Purpose

Rules that hold for the published contract as a whole rather than for one capability: what the generated
OpenAPI document must say about the requests every endpoint accepts. The rules of each capability decide
the bounds; this spec decides how they reach the contract, and how a request that breaks a maximum length is
answered. It also records which operations the contract
marks deprecated, and what it tells a client about them.

## Glossary

| Term | Meaning |
| --- | --- |
| Numeric bound | A limit on the value of a number in a request, enforced by bean validation (`@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero`, `@Digits`) |
| Side | Whether a bound limits a value from below (minimum) or from above (maximum) |
| Maximum length | The most characters a string in a request may have, declared with `@Size(max)` |

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

### API-002 — The old production reads are deprecated, each naming its replacement

- **Status:** Active
- **Rule:** The old production read operations (`getInstantProduction`, `getHourlyProduction`,
  `getDailyProduction`, `getMonthlyProduction`, `getYearlyProduction`, `getDatadisHourlyProduction`,
  `getDatadisDailyProduction`, `getDatadisMonthlyProduction`, `getDatadisYearlyProduction`,
  `getSupplyHourlyProduction`, `getSupplyDailyProduction`, `getSupplyMonthlyProduction`) MUST be marked deprecated
  in the contract, each naming its replacement, with no change to their behaviour.
- **Rationale:** Clients must learn from the contract that these reads are superseded, and by what. They do not
  follow the period and value conventions (EPR, EVL), and their defects made them easy to misuse, as the plant
  detail did until lucoenergia/conluz-web#406. The production syncs and the Huawei configuration are still in use
  and are not deprecated. Removing the reads is a separate decision, after a release cycle.
- **Source:** #570

### API-003 — Every maximum length of a request body is published, and a violation is answered FIELD_TOO_LONG

- **Status:** Active
- **Rule:** Every maximum string length of a request body MUST be declared with `@Size` and published in the contract
  as `maxLength`; publishing it MUST NOT change any other part of the property's schema. A value longer than its
  maximum MUST be answered 400 with one error per field, of code `FIELD_TOO_LONG` and params `field` (the property
  path), `maxLength` and `n` (the value's length), and the response and the logs MUST NOT repeat the value. A request
  with no value longer than its maximum MUST be answered as before.
- **Rationale:** Clients are generated from the contract; a limit it omits is a 400 (or, past a column's size, a 500)
  that a form cannot prevent. A typed code with its parameters lets a client say which field is too long and by how
  much in its own language, while every other refusal keeps the response clients already handle. `@Length` stays
  available where a maximum must not be published, so it is not treated as one. The value may be personal data, so it
  is only measured.
- **Source:** #574

### API-004 — Every address is at most 250 characters

- **Status:** Active
- **Rule:** Every `address` of a request body (`createSupply`, `updateSupply`, `createPlant`, `updatePlant`,
  `createCommunity`, `updateCommunity`, `registerMember`, `updateMemberRecord`, `updateProfile`, `init`) MUST be at
  most 250 characters, as API-003 publishes and answers it. In a CSV import (`importSupplies`, `importMembers`), a row
  whose address, as it would be stored, is longer MUST fail as an invalid row with the constraint `FIELD_TOO_LONG` on
  the column `address`, with the same params, alongside every other rule it breaks; the other rows are imported as
  before.
- **Rationale:** Users, supplies and member records store the address in a column of 250 characters, so a longer one
  failed with a 500; plants and communities have no limit, which let the same address be valid in one place and not
  in another. One limit everywhere, published, makes the failure a field error a client can show. An import reports
  it per row, with the import's existing row structure, so one long address does not refuse the whole file.
- **Source:** #574

## Impossible states

None.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Numeric constraints on operation parameters (`@RequestParam`, `@PathVariable`, `@RequestHeader`) are not covered by the API-001 mechanism | Whether springdoc publishes numeric constraints on parameters correctly has not been verified; no operation parameter carries one today | An operation parameter gains a numeric constraint | #549 |
