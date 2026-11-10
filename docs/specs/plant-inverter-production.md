# Spec — Plant inverter production

| | |
| --- | --- |
| **Prefix** | `PIP` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Plants`; operation `getPlantInverterProductionToday` |
| **Related ADRs** | — |
| **Last reviewed against code** | #565 |

## Purpose

Today's production of a single plant as its Huawei inverter reports it: today's energy, a power curve, the time
of the latest snapshot and whether it is stale. It is derived from the real-time snapshots the Huawei sync stores
for plants with an enabled configuration. This is gross inverter output, which may differ from the distributor's
figures (`plant-energy-metrics.md`) and is never summed with them. The precision of its energy and power figures
follows `energy-values.md`. The rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Snapshot | A real-time reading of the plant's inverter, stored about every 5 minutes. It carries `day_power`: the energy the inverter has counted since its counter last reset, in kWh. |
| Current local day | The day, in the community's time zone, that the current instant falls in, from local midnight. |
| Carry-over | A snapshot of the current local day that still carries the previous local day's total, as defined by PIP-002. |
| Gap | An interval between two consecutive snapshots longer than 15 minutes. |

## Rules

### PIP-001 — The status tells whether today's inverter production can be shown

- **Status:** Active
- **Rule:** The operation MUST report `AVAILABLE` when the plant has an enabled Huawei configuration and at least
  one snapshot from the current local day, `NO_DATA_TODAY` when the configuration is enabled without such a
  snapshot, and `NOT_AVAILABLE` otherwise.
- **Rationale:** The client hides the block when nothing can be shown.
- **Source:** #565

### PIP-002 — The carry-over run counts as zero, and today's energy is never negative

- **Status:** Active
- **Rule:** The leading run of the current local day's snapshots whose `day_power` equals the previous local day's
  last `day_power`, when that value is greater than 0, MUST count as zero production today. Today's energy MUST be
  the latest `day_power` after that run, or 0 if every snapshot of today belongs to it. A decrease MUST never yield
  negative production.
- **Rationale:** The counter resets at local midnight, but a snapshot taken at the boundary can still carry the
  previous day's total, as observed in production data. A day that starts from 0 has no carry-over.
- **Source:** #565

**Scenarios**

```gherkin
Given a plant whose last snapshot of 2026-09-14 has a day_power of 323.36
And whose snapshot at 2026-09-15T00:00:00.98 local has a day_power of 323.36
And whose next snapshot, at 00:05 local, has a day_power of 0
When today's inverter production is read on 2026-09-15
Then the 00:00:00.98 snapshot is carry-over and counts as 0 kWh
And the point at 00:05 is 0 kW, not negative
```

### PIP-003 — The power curve is averaged over real intervals, with gaps as null

- **Status:** Active
- **Rule:** The power curve MUST have one point per pair of consecutive snapshots of the current local day: the
  average power over the interval, from the real timestamps, in kW, timestamped at the later snapshot. An interval
  longer than 15 minutes MUST be null.
- **Rationale:** A gap must not be drawn as production. Snapshots are not exactly 5 minutes apart, and a single
  missing snapshot is normal.
- **Source:** #565

### PIP-004 — The latest snapshot is stale after 60 minutes

- **Status:** Active
- **Rule:** `stale` MUST be true when the latest snapshot is older than 60 minutes.
- **Rationale:** One threshold, on the server.
- **Source:** #565

### PIP-005 — Inverter production is readable by the community's admins only, and exposes no station

- **Status:** Active
- **Rule:** The operation MUST be readable only by a community admin of the plant's community, MUST answer 403 to a
  member of that community and 404 to anyone else, and MUST NOT expose any station code, provider code or
  credential.
- **Rationale:** The block lives on the admin-only plant detail.
- **Source:** #565

## Impossible states

- A negative point on the power curve, or a negative energy today — prevented by PIP-002 and PIP-003: a decrease
  counts as no energy.

## Accepted exceptions

None.
