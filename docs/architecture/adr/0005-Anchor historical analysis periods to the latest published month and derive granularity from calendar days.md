# ADR-0005 — Anchor historical analysis periods to the latest published month and derive granularity from calendar days

- **Status:** Proposed
- **Date:** 2026-10-26
- **Deciders:** Víctor Cañizares
- **Applies to:** `lucoenergia/conluz` (supply and membership energy metrics, period resolution); `lucoenergia/conluz-web` (historical analysis page, period selection)

## Context

Datadis publishes consumption and measured surplus as the days go by, but self-consumption arrives in one batch around the 10th of the following month. Before publication, stored hourly records carry no `self_consumption_energy_kwh`. The consumption series and the monthly pre-aggregate (`DatadisMonthlyAggregationRepositoryInflux#aggregateMonthlyConsumption`) turn the absent field into `0.0`, so an unpublished period is indistinguishable from a measured zero. Energy-metrics coverage counts consumption records, not publication, so an unpublished range shows 100 % coverage. Unverified figures in #375 also suggest that `consumption_kwh` holds gross consumption before publication, which would overstate grid import.

Publication state exists in one place only: `ReferenceMonthResolverImpl#resolveLatestPublishedMonth` (ENM-001…005). It is used by `period=LATEST_PUBLISHED_MONTH` on membership energy metrics, by the hourly profile and, since #408, by supply energy metrics (SEM-001). Under ENM-002, a membership accepts a month published by any of its supplies. No series reports publication per bucket.

In `conluz-web`, the existing period logic (`src/components/Graph/GraphFilter.tsx`, the inline `filterType` / `datesGranularity` blocks in `SupplyDetailPage` and `PlantDetailPage`, `src/utils/getTimeRange.ts`) has three defects:

- it builds ranges from the device clock and zone;
- it measures elapsed milliseconds, so daylight-saving changes move its 60-day cut;
- it sums the whole edge months the monthly series returns, so totals cover more than the selected range without saying so.

The monthly series selects a month when its local day-1 midnight falls inside the bounds, and always returns it whole.

## Decision

1. **Anchor every historical period to the latest published month resolved by the server.** "Todos mis puntos" uses `LATEST_PUBLISHED_MONTH` on membership energy metrics. A single supply uses `LATEST_PUBLISHED_MONTH` on supply energy metrics (SEM-001), never its owner's membership anchor. The presets "Último mes publicado", "Este año" and "Últimos 12 meses" end at that month. A custom range cannot end after it, and the page never reads the current month.
2. **Derive granularity from calendar days in `APP_TIME_ZONE`.**
   - A range of up to 62 calendar days uses daily buckets, with free start and end days.
   - A longer range is snapped to the whole calendar months that contain its start and end dates, and uses monthly buckets. The snapped period is shown to the user.
   - A period is limited to 24 months after snapping.
3. **Never read the device clock or zone** to build, classify or label a period.

Parts 1 and 2 only work together. Snapping to whole months is safe because no month after the anchor can appear. Anchoring is only usable because snapping keeps totals and buckets covering the same period.

## What a zero means on the historical analysis page

Inside the selected period, a zero is a measured zero, with one accepted exception. Under ENM-002, a month counts as published if any of the member's supplies published it. So in "Todos mis puntos", a supply that has not yet published contributes self-consumption 0 for that month.

"Este año" before January is published is a normal, neutral empty state, not an error.

## Alternatives considered

**Calendar periods with unpublished data flagged as provisional.** This would let members see recent consumption. It requires per-bucket publication state in every series and a contract fix, because today the contract calls an unpublished month a measured zero. It would also show grid import that may later be revised downwards. Rejected on cost and correctness risk. It is the natural next step once publication state exists per bucket.

**Keep the existing 60-day elapsed-time cut.** It is the number already in code. But it moves with daylight-saving changes, and it sends two full calendar months (62 days, such as July and August) to monthly buckets, which shows two bars. Rejected.

**Monthly buckets over partial months, with totals from energy metrics.** The chart and the totals would cover different periods. Rejected, because it reintroduces the mismatch the existing pages already have.

**Anchor a single supply to its owner's membership.** It needs no new endpoint. But under ENM-002 a supply could be shown a month it has not published, with its self-consumption reported as a measured zero. Rejected (see SEM-001).

## Consequences

**Positive**

- No unpublished data reaches the historical analysis page. The gap / measured zero / incomplete distinction only has to handle gaps and the ENM-002 exception.
- Totals, chart buckets, table rows and CSV rows always cover the same period.

**Negative**

- Members never see the current month, nor the previous month before about the 10th.
- Switching from "Todos mis puntos" to a single supply can change the anchor month. The page must clamp the period and say so.
- Every future screen that offers historical period selection MUST anchor on a server-resolved month and count calendar days in `APP_TIME_ZONE`. Reusing `GraphFilter` or `getTimeRange` violates this decision.

## Revisit if

- The backend reports publication state per bucket.
- Self-consumption becomes available in near-real time (for example, computed from Shelly readings and partition coefficients).
- The time zone comes from the server per community, replacing `APP_TIME_ZONE`.
- A second community's distributor publishes on a different schedule.

## References

- `conluz`:
  - `ReferenceMonthResolverImpl`
  - `EnergyMetricsPeriod#ofMonth`
  - `docs/specs/` membership energy metrics (ENM-001…005) and `docs/specs/supply-energy-metrics.md` (SEM-001)
- `conluz-web`:
  - `src/pages/home/member/monthBounds.ts`
  - `src/utils/getTimeRange.ts`
  - `src/components/Graph/GraphFilter.tsx`
- Issues: #375, #383, #386, #405, #408