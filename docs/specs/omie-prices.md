# Spec — OMIE prices

| | |
| --- | --- |
| **Prefix** | `PRC` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Prices`; operation `syncPrices`. Prices are also synchronised by the scheduled job `SyncDailyPricesJob` |
| **Related ADRs** | — |
| **Last reviewed against code** | #522 |

## Purpose

The day-ahead market prices published by OMIE, downloaded once a day for the last seven days, today and
tomorrow, and stored in the `omie_prices_kwh` measurement. A platform admin can also re-synchronise an earlier range
of days on demand. This spec covers how a day's file is turned into timestamped prices and how a range
of days is re-synchronised; the rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Market day | A calendar day in Spanish time (Europe/Madrid). It lasts 23 hours when the clocks go forward, 25 hours when they go back, and 24 hours otherwise. |
| Period | One row of a day's OMIE file, numbered from 1. It lasts 15 minutes in current files and 60 minutes in historical ones. |

## Rules

### PRC-001 — Prices are placed by period in the market day's time

- **Status:** Active
- **Rule:** The price of period *n* of a day's OMIE file MUST be stored at the start of that market
  day in Europe/Madrid plus (*n* − 1) times the file's period length, whatever time zone the
  application is configured with. A market day therefore has 92, 96 or 100 quarters, or 23, 24 or 25
  hours.
- **Rationale:** Counting periods in local wall-clock time maps the hour skipped when the clocks go
  forward onto the next one, and cannot represent the hour repeated when they go back. A rule based
  on elapsed time gives every period its own instant on every day.
- **Source:** #516

### PRC-002 — A file that does not fill its day is rejected

- **Status:** Active
- **Rule:** If the number of periods in a day's file does not fill that market day's real length at
  15 or 60 minutes per period, or the periods are not consecutive from 1, no price of that day MUST be
  stored.
- **Rationale:** The period length is not stated in the file; it is inferred from the period count.
  A count that fits neither length means the format has changed, and guessing would store prices at
  the wrong instants.
- **Source:** #516

### PRC-003 — One day's failure does not block the others

- **Status:** Active
- **Rule:** A day whose file cannot be fetched or is rejected MUST NOT prevent the other days of the
  same synchronisation from being stored. Any failure to parse a day's file rejects that day.
- **Rationale:** The job synchronises seven days at a time. A single rejected day (the 25-hour day of
  2026-10-25) used to abort the whole run, so no price was stored for a week. A malformed row, such as
  a price that is not a number or a missing column, used to escape as a different exception and abort
  the run the same way.
- **Source:** #516; #520 (any parse failure, not only a detected format mismatch).

### PRC-004 — The stored price is the Spanish system's marginal price

- **Status:** Active
- **Rule:** The price stored for each period MUST be the marginal price of the Spanish system (6th
  column of OMIE's `marginalpdbc` files), never the Portuguese one.
- **Rationale:** Each row of the file carries the marginal price of the Portuguese system and then
  that of the Spanish system. They are equal while the Iberian market is coupled, but differ when it
  splits (for example 0 and 3.25 €/MWh in period 8 of 2024-04-05). Conluz communities are Spanish, so
  the Portuguese price misprices their energy whenever the market splits.
- **Source:** #519; OMIE `marginalpdbc` column layout: year; month; day; period; Portuguese price;
  Spanish price.

### PRC-005 — A platform admin may re-synchronise a range of market days

- **Status:** Active
- **Rule:** An enabled platform admin MAY re-synchronise the prices of a half-open range of market days
  `[startDate, endDate)`, of at most 31 days and extending no further than tomorrow in Europe/Madrid.
  Each day's stored prices are replaced by the ones OMIE serves at that moment. A range beyond
  tomorrow is rejected, not clipped.
- **Rationale:** The job can only repair the last seven days, so a day that left that window could
  not be repaired without a code change: gaps left by a failed sync, prices shifted on the day the
  clocks go forward (#516), and Portuguese prices stored instead of Spanish ones (#519). The range is
  half-open for consistency with the energy periods (EPR-001, #409). It is rejected rather than
  clipped because an action that writes data must not silently do less than asked. The 31-day limit
  comes from one call per day to omie.es with a 30-second timeout, so the worst case is about
  15 minutes; a caller with more days to repair sends several requests. Tomorrow is allowed because
  OMIE publishes the next day's prices around 13:00 Europe/Madrid.
- **Source:** #520

### PRC-006 — A re-synchronisation reports each day

- **Status:** Active
- **Rule:** A re-synchronisation MUST report each day of the range as stored, with the number of
  prices written, or skipped, with the reason.
- **Rationale:** Without a per-day outcome the action cannot be checked, and skipped days are lost.
  A day skipped because it could not be fetched is told apart from one OMIE has no file for, because
  only the first is worth retrying at once.
- **Source:** #520

### PRC-007 — A re-synchronisation is idempotent

- **Status:** Active
- **Rule:** A re-synchronisation MUST be idempotent: repeating the same request, including after an
  interrupted one, MUST leave the stored prices as a single successful run would.
- **Rationale:** A request can be cut mid-range, by a proxy or client timeout, with some days rewritten
  and no report. Retrying the same request unchanged is the remedy, and it is safe only if repeating it
  converges on what one successful run stores.
- **Source:** #520

### PRC-008 — The daily sync runs at 21:00 Madrid and includes tomorrow

- **Status:** Active
- **Rule:** The daily price sync MUST run once a day at 21:00 Europe/Madrid and MUST sync the market
  days `[today−7, today+2)` in Europe/Madrid, including tomorrow.
- **Rationale:** OMIE publishes tomorrow's prices around 13:00 Europe/Madrid. Syncing them the evening
  before makes them available before the day starts; a morning run only stored them once the day had
  begun, and not at all if that run failed. 21:00 is a deliberate choice, leaving hours of margin after
  publication, and must not be moved earlier without reopening this decision. The schedule is
  evaluated in Europe/Madrid, never in the JVM's default zone. A failed run is repaired by the next
  run, whose range still covers the missed days, or by a manual re-synchronisation (PRC-005).
- **Source:** #522

## Impossible states

- Two prices of the same day stored at the same instant — prevented by PRC-001.

## Accepted exceptions

None.
