# Spec — Plant inverter production

| | |
| --- | --- |
| **Prefix** | `PIP` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Plants`; operation `getPlantInverterProductionToday`. Tag `Supplies`; operation `getSupplyEnergyToday` |
| **Related ADRs** | — |
| **Last reviewed against code** | #595 |

## Purpose

Today's production of a single plant as its Huawei inverter reports it: today's energy, a power curve, the time
of the latest snapshot and whether it is stale. It is derived from the real-time snapshots the Huawei sync stores
for plants with an enabled configuration. This is gross inverter output, which may differ from the distributor's
figures (`plant-energy-metrics.md`) and is never summed with them. The same snapshots also give the share of
today's production assigned to a supply point, through its partition coefficients (`getSupplyEnergyToday`). The
precision of its energy and power figures follows `energy-values.md`. The rest of the capability is specified
lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Snapshot | A real-time reading of the plant's inverter, stored about every 5 minutes. It carries `day_power`: the energy the inverter has counted since its counter last reset, in kWh. |
| Current local day | The day, in the community's time zone, that the current instant falls in, from local midnight. |
| Carry-over | A snapshot of the current local day that still carries the previous local day's total, as defined by PIP-002. |
| Gap | An interval between two consecutive snapshots longer than 15 minutes. |
| β | The partition coefficient of a supply point in a plant, on the 0–1 scale, in force over a half-open validity period (`sharing-agreements.md`). |
| Leading interval | The interval from local midnight to a plant's first snapshot of the current local day, carrying the energy that snapshot counts. |
| Slot | A fixed 5-minute period of the current local day, the first starting at local midnight. |
| Plants used | The plants where the supply has a β in force at some instant of the current local day so far, an enabled Huawei configuration, and at least one snapshot of the current local day. |

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

### PIP-004 — Today's inverter data is stale after 15 minutes

- **Status:** Active
- **Rule:** On both operations, `stale` MUST be true when the latest snapshot of the current local day is more than
  15 minutes old. A snapshot exactly 15 minutes old is not stale.
- **Rationale:** One threshold, on the server, shared by the plant and supply views, so the same plant is never
  stale in one and current in the other. Snapshots arrive about every 5 minutes, and a client polling every 5 minutes
  must not present a feed that stopped long ago as current. The real-time sync stores snapshots around the clock,
  at night too, so the threshold does not flag every night.
- **Source:** #565; threshold lowered from 60 minutes and extended to `getSupplyEnergyToday` in #595

### PIP-005 — Inverter production is readable by the community's admins only, and exposes no station

- **Status:** Active
- **Rule:** The operation MUST be readable only by a community admin of the plant's community, MUST answer 403 to a
  member of that community and 404 to anyone else, and MUST NOT expose any station code, provider code or
  credential.
- **Rationale:** The block lives on the admin-only plant detail.
- **Source:** #565

### PIP-006 — The supply view describes the current local day, and reports figures only when available

- **Status:** Active
- **Rule:** `getSupplyEnergyToday` MUST describe the current local day in the time zone of the supply's community.
  Every field of its production except the status MUST be null unless the status is `AVAILABLE`.
- **Rationale:** One meaning of "today" across the inverter operations, and a client that never has to tell a missing
  figure from a zero one.
- **Source:** #595

### PIP-007 — The supply view's status is the first cause that applies

- **Status:** Active
- **Rule:** The status MUST be the first that applies of: `NO_COEFFICIENT_TODAY` (no β in force at any instant of the
  current local day so far, in any plant), `NOT_AVAILABLE` (none of the plants with such a β has an enabled Huawei
  configuration), `NO_DATA_TODAY` (none of those with an enabled configuration has a snapshot of the current local
  day), `AVAILABLE`.
- **Rationale:** The client renders a distinct state per cause and must not deduce it.
- **Source:** #595

### PIP-008 — Assigned energy weighs each interval by the β in force at its start

- **Status:** Active
- **Rule:** The assigned energy MUST be the sum, over the plants used and over each plant's leading interval and the
  intervals between its consecutive snapshots of the current local day, of the interval's energy (PIP-002) times the
  β in force at the interval's start, 0 when none is. Gaps MUST be included. It MUST be rounded to 3 decimals once,
  after summing.
- **Rationale:** Weighing by the interval's start stays correct when β changes mid-day, and counting gaps and the
  leading interval never loses energy: with a single plant and a constant β, the assigned energy is today's energy
  (PIP-002) times β. The one exception is a decrease of the inverter counter within the day: each interval counts a
  decrease as nothing (PIP-002), so the sum of the intervals can then exceed the latest counter value.
- **Source:** #595

**Scenarios**

```gherkin
Given a supply whose β in a plant is 0.1 until 10:07 local and 0.3 from 10:07
And snapshots of that plant at 10:05 and 10:10 local, 0.6 kWh apart
When today's assigned production is read
Then the interval from 10:05 to 10:10 contributes 0.6 × 0.1 kWh
```

### PIP-009 — The assigned curve uses fixed 5-minute slots

- **Status:** Active
- **Rule:** The assigned curve MUST have one point per slot ending at or before the latest snapshot of the plants
  used, at the slot's end. A point MUST be the assigned energy of the slot over its 5 minutes, each interval's energy
  being split over the slots it overlaps in proportion to the time overlapped. A point MUST be null when a gap
  overlaps the slot, or when a plant's intervals do not cover all of it. The current assigned power MUST be the last
  point.
- **Rationale:** Fixed slots can be summed across plants and, later, compared slot by slot with consumption. A slot
  only partly known is not drawn as production.
- **Source:** #595

### PIP-010 — Several plants are summed, and their β is not reported

- **Status:** Active
- **Rule:** When the supply has a β in more than one plant used, energy and power, assigned and of the plants, MUST be
  summed over them, a slot MUST be null when any of them has no value in it, and the partition coefficient MUST be
  null. With a single plant used, the partition coefficient MUST be the β in force now in that plant, 0 when none is.
- **Rationale:** A sum of β from different plants has no meaning, and a partial sum must not be drawn as the
  supply's total.
- **Source:** #595

**Scenarios**

```gherkin
Given a supply with a β of 0.1 in one plant and 0.2 in another
And both plants have snapshots of today
When today's assigned production is read
Then the assigned energy is the sum of each plant's energy times its β
And the partition coefficient is null
```

### PIP-011 — The supply view is readable by the supply's owner and community admins only

- **Status:** Active
- **Rule:** `getSupplyEnergyToday` MUST be readable by the supply's owner while their membership in the supply's
  community is active, and by the active community admins of that community. Every other caller, a platform admin
  who is not a member included, MUST get 404, as MUST a request for a supply that does not exist.
- **Rationale:** The same visibility as the supply itself (SUP-003); there is no platform-admin bypass.
- **Source:** #595

## Impossible states

- A negative point on the power curve, or a negative energy today — prevented by PIP-002 and PIP-003: a decrease
  counts as no energy.
- A negative assigned energy or assigned power — prevented by PIP-002 and PIP-008: interval energy is never negative,
  and β is on the 0–1 scale.

## Accepted exceptions

None.
