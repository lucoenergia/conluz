# Spec — Supplies

| | |
| --- | --- |
| **Prefix** | `SUP` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operations `createSupply`, `importSupplies`, `deleteSupply`, `getSupply`, `getAllSupplies`, `getSuppliesByUserId`, `updateSupply`, `enableSupply`, `disableSupply`, `transferSupplyOwnership` |
| **Related ADRs** | — |
| **Last reviewed against code** | #585 |

## Purpose

A supply is a member's point of consumption (CUPS) in a community. This spec covers the rules that keep a supply
tied to a member of its community, what deleting one does, who reads one, how it references its plant, how
supplies are imported from a file, which codes a supply may have, and how a supply is transferred to another owner. It
covers only the rules #412, #411, #532 and #585 touched.

## Glossary

| Term | Meaning |
| --- | --- |
| Supply | A point of consumption, identified by its CUPS code, owned by one user at a time and belonging to one community. |
| Transfer | A change of a supply's owner to another member of its community, made by a community admin from an effective date (`transferSupplyOwnership`), and recorded. |
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

### SUP-008 — A community admin transfers a supply to another member

- **Status:** Active
- **Rule:** A community admin of a supply's community MAY transfer the supply to another user
  (`transferSupplyOwnership`). The supply's owner MUST be answered 403. Any caller who cannot see the supply
  (SUP-003), a platform admin included, MUST be answered 404.
- **Rationale:** The holder of a supply changes in real life (a death, a sale), and the supply must follow without
  being deleted and created again, which SUP-001 refuses for a supply in use and which would lose its coefficient
  history. Changing the owner is a community-management act, like editing the supply, so it has the same access.
- **Source:** #585

### SUP-009 — A supply is transferred only to a member of its community, under the owners' locks

- **Status:** Active
- **Rule:** A transfer MUST lock the community, then the previous and the new owner's account rows in ascending id
  order, then the supply, and MUST check that the new owner has a membership in the supply's community, in any state.
  A new owner without one, or whose account or membership is deleted while the supply is being transferred, MUST be
  answered exactly like an unknown owner (404 `SUPPLY_OWNER_NOT_FOUND`). Transferring the supply to its current owner
  MUST be refused with 409 `SUPPLY_ALREADY_OWNED_BY_USER`. A refused transfer MUST change nothing.
- **Rationale:** The same invariant and race protection as SUP-002 and MEM-029, applied to both owners: deleting an
  owner or their membership locks the same user row first, so the deletion and the transfer run one after the other,
  and the deletion then sees the supply or the transfer finds the membership gone. The community lock makes two
  transfers of the same community run one after the other, so the previous owner a transfer reads cannot change
  before it writes. The ascending order keeps two transfers between the same users from waiting for each other.
- **Source:** #585, SUP-002, MEM-029, ADR-0003 §11

### SUP-010 — A transfer's effective date is not in the future and follows the previous transfer's

- **Status:** Active
- **Rule:** A transfer's effective date MUST NOT be after today in the community's time zone (400
  `SUPPLY_TRANSFER_DATE_IN_FUTURE`), and MUST be strictly after the effective date of the supply's previous transfer
  (409 `SUPPLY_TRANSFER_DATE_NOT_AFTER_PREVIOUS`). Only the transfers of the same supply count.
- **Rationale:** The date records when the holder changed at the distributor, which has already happened. Strictly
  ordered dates keep a supply's transfers usable as consecutive half-open ownership periods (EPR-001), each starting
  on its effective date.
- **Source:** #585, EPR-001

### SUP-011 — Every transfer is recorded, and the records are never changed

- **Status:** Active
- **Rule:** Every transfer MUST be recorded with the supply, the previous owner (none if the supply had no owner),
  the new owner, the effective date, whether the history was included, who made it and when. Records MUST NOT be
  updated. They MUST be deleted only with their supply, when SUP-001 allows deleting it. A supply without an owner MAY
  be transferred.
- **Rationale:** The record is the audit trail: there is no other record of who owned a supply when, or of who
  changed it. Supplies left without an owner by deletions before #412 need a way to get one.
- **Source:** #585

### SUP-012 — After a transfer, everything read through the supply follows the new owner

- **Status:** Active
- **Rule:** After a transfer, every read through the supply (SUP-003) and every figure of a membership MUST follow
  the new owner for the supply's whole history, including the periods before the effective date. Every later write
  of the supply MUST keep the new owner.
- **Rationale:** The transfer carries the supply's history to the new owner, which is the right outcome when the
  supply stays in the same household. A write of a supply loaded before the transfer, such as the Datadis supply
  sync's, must not undo it.
- **Source:** #585

## Impossible states

- A supply whose owner has no membership in its community, created or transferred after #412 — prevented by
  SUP-002, SUP-009 and MEM-029.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A deleted supply's consumption series stay in the time-series database, keyed by CUPS; a supply created again with the same CUPS shows them | The series are measurements of the point of consumption, and deleting them cannot be undone or made part of the database transaction | A rule decides what happens to the series of a deleted supply | #412 |
| The previous owner's membership figures lose a transferred supply for every period, including the months they owned it (SUP-012) | Ownership is a single column read when the request runs; the transfer records are shaped as periods so this can be lifted without a backfill | Figures per owner and period are needed | #585 |
| Two supplies with the same CUPS across lengths or case can be written after #532, by two creations or code changes running at the same time | SUP-007 is checked by the application before the write, without a lock or a database constraint, so two concurrent writes can both pass the check | A database constraint or a lock makes SUP-007 hold for concurrent writes | #532 |
