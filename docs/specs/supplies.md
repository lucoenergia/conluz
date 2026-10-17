# Spec — Supplies

| | |
| --- | --- |
| **Prefix** | `SUP` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operations `createSupply`, `createSuppliesWithFile`, `deleteSupply` |
| **Related ADRs** | — |
| **Last reviewed against code** | #412 |

## Purpose

A supply is a member's point of consumption (CUPS) in a community. This spec covers the rules that keep a supply
tied to a member of its community, and what deleting one does. It covers only the rules #412 touched.

## Glossary

| Term | Meaning |
| --- | --- |
| Supply | A point of consumption, identified by its CUPS code, owned by one user and belonging to one community. |
| Plant | A generation installation attached to a supply. |
| Partition coefficient | The share of a plant's production a sharing agreement assigns to a supply. |

## Rules

### SUP-001 — A supply is deleted only when no plant or sharing agreement uses it

- **Status:** Active
- **Rule:** Deleting a supply (`deleteSupply`) MUST be refused with 409 `SUPPLY_HAS_PLANTS` when a plant is
  attached to it, and with 409 `SUPPLY_HAS_PARTITION_COEFFICIENTS` when a sharing agreement, in any status, holds a
  partition coefficient for it; nothing MUST be deleted. Otherwise the supply MUST be deleted with its contract,
  distributor and Shelly details, and its owner and the owner's memberships MUST be kept. Only a community admin of
  the supply's community MAY delete it: anyone else who can see the supply MUST be refused with 403, and anyone who
  cannot, a platform admin included, MUST be answered 404. The deletion MUST lock the supply row exclusively before
  checking, so a plant or coefficient being inserted at the same time is waited for and then refuses it.
- **Rationale:** A plant carries the community's production and its sharing agreements, and the coefficients are
  the content of an agreement: deleting the supply must not remove or change them as a side effect. A supply that
  is still used is taken out of use by disabling it (`disableSupply`), which keeps the data. Deleting a supply is
  what lets its owner's membership (MEM-029) and account (ACC-009) be deleted.
- **Source:** #412

### SUP-002 — A supply is created only for a member of its community

- **Status:** Active
- **Rule:** Creating a supply (`createSupply`, `createSuppliesWithFile`) MUST lock the owner's account row and then
  check that the owner has a membership in the supply's community, in any state. An owner without one, or whose
  account or membership is deleted while the supply is being created, MUST be answered exactly like an unknown
  owner, and no supply MUST be created.
- **Rationale:** The owner of a supply belongs to the supply's community (#341). Deleting the owner (ACC-009) or
  their membership (MEM-029) locks the same row first, so the creation and the deletion run one after the other:
  the deletion then sees the supply and is refused, or the creation finds the owner gone. Without the lock both
  could succeed and leave a supply whose owner is not a member, or the deletion could fail on a foreign key.
- **Source:** #341, #412

## Impossible states

- A supply whose owner has no membership in its community, created after #412 — prevented by SUP-002 and MEM-029.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A deleted supply's consumption series stay in the time-series database, keyed by CUPS; a supply created again with the same CUPS shows them | The series are measurements of the point of consumption, and deleting them cannot be undone or made part of the database transaction | A rule decides what happens to the series of a deleted supply | #412 |
| A plant or partition coefficient written for a supply while the supply is being deleted fails with an unhandled foreign-key error once the deletion commits | Plant creation and coefficient saving do not lock the supply row; the deletion itself is safe (SUP-001) | Plant creation and coefficient saving lock the supply row before writing | #412 |
