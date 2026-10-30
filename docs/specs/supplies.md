# Spec — Supplies

| | |
| --- | --- |
| **Prefix** | `SUP` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operations `createSupply`, `importSupplies`, `deleteSupply`, `getSupply`, `getAllSupplies`, `getSuppliesByUserId`, `updateSupply`, `enableSupply`, `disableSupply` |
| **Related ADRs** | — |
| **Last reviewed against code** | #532 |

## Purpose

A supply is a member's point of consumption (CUPS) in a community. This spec covers the rules that keep a supply
tied to a member of its community, what deleting one does, who reads one, how it references its plant, how
supplies are imported from a file, and which codes a supply may have. It covers only the rules #412, #411 and #532
touched.

## Glossary

| Term | Meaning |
| --- | --- |
| Supply | A point of consumption, identified by its CUPS code, owned by one user and belonging to one community. |
| Plant | A generation installation attached to a supply. |
| Partition coefficient | The share of a plant's production a sharing agreement assigns to a supply. |
| CUPS | The code of a point of consumption, 22 characters long. It may be written with its first 20 characters only, which stand for the 22 obtained by adding `0F`. |

## Rules

### SUP-001 — A supply is deleted only when no plant or sharing agreement uses it

- **Status:** Active
- **Rule:** Deleting a supply (`deleteSupply`) MUST be refused with 409 `SUPPLY_HAS_PLANTS` when a plant is
  attached to it, and with 409 `SUPPLY_HAS_PARTITION_COEFFICIENTS` when a sharing agreement, in any status, holds a
  partition coefficient for it; nothing MUST be deleted. Otherwise the supply MUST be deleted with its contract,
  distributor and Shelly details, and its owner and the owner's memberships MUST be kept. Only a community admin of
  the supply's community MAY delete it: anyone else who can see the supply MUST be refused with 403, and anyone who
  cannot, a platform admin included, MUST be answered 404. The deletion MUST lock the supply row exclusively before
  checking, so a plant or coefficient being inserted at the same time is waited for and then refuses it. Every
  writer of a plant or a partition coefficient that references a supply (creating a plant, moving a plant to another
  supply, writing an agreement's coefficients) MUST lock the supply against deletion before writing, and answer the
  supply's 404 when it is gone, so a writer that runs while the supply is being deleted waits for the deletion and is
  then answered 404, never a foreign-key error.
- **Rationale:** A plant carries the community's production and its sharing agreements, and the coefficients are
  the content of an agreement: deleting the supply must not remove or change them as a side effect. A supply that
  is still used is taken out of use by disabling it (`disableSupply`), which keeps the data. Deleting a supply is
  what lets its owner's membership (MEM-029) and account (ACC-009) be deleted. The writer's lock conflicts only with
  the deletion's, so writers never wait for each other or for an update of the supply (ADR-0003 §11).
- **Source:** #412. #480 made the writers lock the supply, which removed the accepted exception for a writer racing
  the deletion.

### SUP-002 — A supply is created only for a member of its community

- **Status:** Active
- **Rule:** Creating a supply (`createSupply`, `importSupplies`) MUST lock the owner's account row and then
  check that the owner has a membership in the supply's community, in any state. An owner without one, or whose
  account or membership is deleted while the supply is being created, MUST be answered exactly like an unknown
  owner, and no supply MUST be created.
- **Rationale:** The owner of a supply belongs to the supply's community (#341). Deleting the owner (ACC-009) or
  their membership (MEM-029) locks the same row first, so the creation and the deletion run one after the other:
  the deletion then sees the supply and is refused, or the creation finds the owner gone. Without the lock both
  could succeed and leave a supply whose owner is not a member, or the deletion could fail on a foreign key.
- **Source:** #341, #412. #532 replaced `createSuppliesWithFile` with `importSupplies`, which creates each row as
  `createSupply` does.

### SUP-003 — A supply's owner reads it only while their membership in its community is active

- **Status:** Active
- **Rule:** The owner of a supply MUST be able to read it, and everything read through it, only while their
  membership in the supply's community is active. An owner whose membership there is pending or disabled MUST be
  answered exactly as someone with no membership in that community: 404 on `getSupply`, `getSupplyEnergyMetrics`,
  the supply's consumption and production series, its partition coefficients and `createPlant`, and the supply left
  out of `getSuppliesByUserId`, even when they ask for their own supplies. A community admin's access is unchanged.
- **Rationale:** Owning a supply is not access (MEM-004). Before #411 ownership alone opened every supply read, so a
  suspended or not yet accepted member kept reading their community's supply data through the supplies they own, and
  a response gaining community data (such as the supply's plant) would have reached them too. The listing applies the
  same rule as a single read, so it never carries a supply whose own read answers 404.
- **Source:** #411, MEM-004

### SUP-004 — A supply references its plant when it is a plant's own supply

- **Status:** Active
- **Rule:** Every operation that answers a supply (`getSupply`, `getAllSupplies`,
  `getSuppliesByUserId`, `createSupply`, `updateSupply`, `enableSupply`, `disableSupply`) MUST
  reference the plant whose own supply it is, and MUST answer no plant for any other supply. A
  listing MUST read those plants with one query for the whole page, never one per supply. The plant
  is shown to whoever may read the supply (SUP-003).
- **Rationale:** Only a plant pointed at its supply, so a client could not label a supply as a plant's
  supply, nor explain why its self-consumption ratio does not apply (SEM-003). A supply is the supply
  of at most one plant (PLT-002), so the reference is a single plant. Every caller who may read a
  supply is an active member of its community, who may also read its plants.
- **Source:** #411

### SUP-005 — A community admin imports supplies from a CSV file, each row created as `createSupply` does

- **Status:** Active
- **Rule:** Only an active community admin of the community MAY import supplies into it (`importSupplies`). The file
  MUST follow the same encoding, separator, header, row-limit and empty-row rules as MEM-030, with its own
  `SUPPLY_IMPORT_*` codes. Columns: `code`, `address`, `personalId` required; `addressRef`, `name` optional; no other.
  Each row MUST be created as `createSupply` does, in a transaction of its own, and MUST get one result with its line,
  outcome, code and the broken rules by column and constraint, never echoing the values.
- **Rationale:** Same onboarding task as the member import, same files from Excel; one client implementation. Row
  isolation keeps a large file usable when a few rows fail.
- **Source:** #532

### SUP-006 — A supply code written to a supply is a CUPS, stored upper-cased

- **Status:** Active
- **Rule:** A supply code MUST be upper-cased, MUST contain no whitespace and MUST be 20 or 22 characters long, on
  `createSupply`, `importSupplies`, and `updateSupply` when the code changes. Codes stored before this rule are not
  revalidated.
- **Rationale:** Reuses the only CUPS rule the distributor file already enforces, so a code accepted at creation can
  be filed. Validating on unchanged updates would lock legacy supplies out of editing.
- **Source:** #532

### SUP-007 — No two supplies have the same CUPS, in either length or case

- **Status:** Active
- **Rule:** A supply MUST NOT be created, or its code changed, if another supply has the same CUPS once both are
  completed to 22 characters (`0F`) and compared case-insensitively. Same community → `SUPPLY_ALREADY_EXISTS`; another
  community → `SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY`, naming neither the community nor the owner. Accepted risk: the
  second code reveals that the CUPS is registered on the platform.
- **Rationale:** A supply belongs to one community and never moves; a duplicate across lengths breaks the distributor
  file. The admin needs to know the CUPS cannot be registered here, and the creation would fail anyway.
- **Source:** #532

## Impossible states

- A supply whose owner has no membership in its community, created after #412 — prevented by SUP-002 and MEM-029.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A deleted supply's consumption series stay in the time-series database, keyed by CUPS; a supply created again with the same CUPS shows them | The series are measurements of the point of consumption, and deleting them cannot be undone or made part of the database transaction | A rule decides what happens to the series of a deleted supply | #412 |
| Two supplies with the same CUPS across lengths or case can be written after #532, by two creations or code changes running at the same time | SUP-007 is checked by the application before the write, without a lock or a database constraint, so two concurrent writes can both pass the check | A database constraint or a lock makes SUP-007 hold for concurrent writes | #532 |
