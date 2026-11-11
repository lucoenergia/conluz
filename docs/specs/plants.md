# Spec — Plants

| | |
| --- | --- |
| **Prefix** | `PLT` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Plants`; operations `deletePlant`, `createPlant`, `updatePlant`, `getPlantById`, `getAllPlants` |
| **Related ADRs** | — |
| **Last reviewed against code** | #567 |

## Purpose

A plant is a generation installation attached to a supply of a community. This spec covers what deleting one does,
which supply a plant may be attached to, the powers a plant records (its peak and nominal power), and its CIL. It
covers only the rules #437, #411 and #567 touched.

## Glossary

| Term | Meaning |
| --- | --- |
| Plant | A generation installation attached to a supply. |
| Sharing agreement | The community's record of how a plant's energy is shared among supplies, with its distributor files. |
| Partition coefficient | The share of a plant's production a sharing agreement assigns to a supply. |
| Huawei configuration | The credentials and settings a plant uses to reach the Huawei FusionSolar API (HCF). |
| Peak power | The sum of the plant's modules, in kWp. |
| Nominal power | The most the plant can export to the grid, in kW, as capped by its inverters and its grid connection conditions. It may be below or above the peak power. |
| CIL | Código de la Instalación de producción a efectos de Liquidación: the Spanish settlement code of a production installation, assigned by the distribution company's metering party; 25 alphanumeric characters (REE, SIMEL). |

## Rules

### PLT-001 — A plant is deleted only when no sharing agreement or partition coefficient uses it

- **Status:** Active
- **Rule:** Deleting a plant (`deletePlant`) MUST be refused with 409 `PLANT_HAS_SHARING_AGREEMENTS` when a sharing
  agreement, in any status, references it, and with 409 `PLANT_HAS_PARTITION_COEFFICIENTS` when a partition
  coefficient references it; nothing MUST be deleted. Otherwise the plant MUST be deleted with its Huawei
  configuration, in the same transaction. Only a community admin of the plant's community MAY delete it: anyone else
  who can see the plant MUST be refused with 403, and anyone who cannot MUST be answered 404. The deletion MUST lock
  the plant row exclusively before checking, so a sharing agreement or coefficient being inserted at the same time is
  waited for and then refuses it. Every writer of a sharing agreement, a partition coefficient or a Huawei
  configuration that references a plant MUST lock the plant against deletion before writing, and answer the plant's
  404 when it is gone, so a writer that runs while the plant is being deleted waits for the deletion and is then
  answered 404, never a foreign-key error; a Huawei configuration written just before the deletion is deleted with
  the plant.
- **Rationale:** A sharing agreement, even a draft or a superseded one, is the community's record of how energy was
  shared, with its distributor files, and the coefficients are its content: deleting the plant must not remove them as
  a side effect, and a plant cannot be disabled, so a plant that holds them is kept. The Huawei configuration only
  serves the plant, so it has no meaning without it. Without the lock, an agreement committed between the check and
  the delete would make the deletion fail on a foreign key with an unhandled error, and without the writer's lock, a
  writer that read the plant before the deletion would fail the same way after it. The writer's lock conflicts only
  with the deletion's, and a Huawei configuration writer takes it before the configuration row, as the deletion does
  (ADR-0003 §11).
- **Source:** #437. #480 made the writers lock the plant, which removed the accepted exception for a writer racing
  the deletion.

### PLT-002 — A supply is the supply of at most one plant

- **Status:** Active
- **Rule:** Creating a plant on a supply that already has a plant (`createPlant`), or moving a plant to
  such a supply (`updatePlant`), MUST be refused with 409 `SUPPLY_ALREADY_HAS_PLANT`, and nothing MUST
  be written. Updating a plant that keeps its own supply is not a conflict. Two writers racing for the
  same supply MUST end with one plant and the same refusal for the other, never an unhandled error.
- **Rationale:** A supply references the plant whose own supply it is (SUP-004), and the
  self-consumption ratio leaves that supply out (SEM-003, ENM-007): both need the link to name a single
  plant. Nothing kept that before: the schema allowed several plants on one supply. The writers check
  before writing, and the database constraint catches the race between two of them.
- **Source:** #411

### PLT-003 — A plant has a nominal power above 0, unconstrained by its peak power

- **Status:** Active
- **Rule:** A plant MUST have a nominal power (`nominalPowerKW`), the power it can export to the grid in kW, greater
  than 0, on `createPlant` and `updatePlant`. It MUST NOT be constrained relative to the peak power (`peakPowerKWp`);
  a value that is not above 0 MUST be refused with 400 and nothing written.
- **Rationale:** Peak and nominal power differ when inverters or grid connection conditions cap export (Luco: 80 kWp,
  60 kW). An oversized inverter makes nominal above peak possible, so no ordering holds in general.
- **Source:** #567

### PLT-004 — A plant's CIL is normalised, and is 25 ASCII alphanumeric characters

- **Status:** Active
- **Rule:** A plant MAY have a CIL. On `createPlant` and `updatePlant` the CIL MUST be upper-cased and stripped of all
  whitespace; empty after that means no CIL. A non-empty CIL that is not exactly 25 ASCII alphanumeric characters
  MUST be refused with 400 `PLANT_CIL_INVALID` and nothing written.
- **Rationale:** The CIL is assigned by the distribution company and is 25 alphanumeric characters (REE SIMEL).
  Normalising lets a code pasted from a distributor document be compared reliably. Optional because existing plants
  and plants still being registered have none.
- **Source:** #567

**Scenarios**

```gherkin
Given a plant written with the CIL " es0031406912345678ab1f001 "
When it is read
Then its CIL is "ES0031406912345678AB1F001"

Given a plant that has a CIL
When it is updated without a CIL, or with an empty or blank one
Then it has no CIL
```

### PLT-005 — A CIL identifies at most one plant

- **Status:** Active
- **Rule:** A CIL MUST identify at most one plant across the platform. Creating or updating a plant with a CIL another
  plant holds MUST be refused with 409 `PLANT_CIL_ALREADY_IN_USE` and nothing written; keeping a plant's own CIL is
  not a conflict. Two racing writers MUST end with one plant holding it and the same refusal for the other, never an
  unhandled error.
- **Rationale:** The CIL identifies a single production installation for settlement; two plants with one CIL would
  make surplus claims ambiguous. The writer checks first; a unique constraint on the normalised value catches the
  race, as PLT-002 does for supplies.
- **Source:** #567

## Impossible states

- Two plants attached to the same supply — prevented by PLT-002 and the `plants_supply_id_uq` unique
  constraint. A database holding such plants does not start the release that adds the constraint until
  they are resolved by hand.

- Two plants holding the same CIL — prevented by PLT-005 and the `plants_cil_uq` unique constraint.

- A sharing agreement, partition coefficient or Huawei configuration whose plant does not exist — prevented by the
  foreign keys to `plants`, which PLT-001 refuses or clears before deleting the plant.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| A deleted plant's production series stay in the time-series database, keyed by its provider code (`station_code`); a plant created again with the same provider code shows them | The series are measurements of the installation, and deleting them cannot be undone or made part of the database transaction | A rule decides what happens to the series of a deleted plant, as for supplies (SUP-001) | #437 |
