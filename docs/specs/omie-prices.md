# Spec — OMIE prices

| | |
| --- | --- |
| **Prefix** | `PRC` |
| **Status** | Active |
| **Contract** | None: prices are synchronised by the scheduled job `SyncDailyPricesJob`, not by an operation |
| **Related ADRs** | — |
| **Last reviewed against code** | #516 |

## Purpose

The day-ahead market prices published by OMIE, downloaded once a day for the last seven days and
stored in the `omie_prices_kwh` measurement. This spec covers only how a day's file is turned into
timestamped prices; the rest of the capability is specified lazily, as issues touch it.

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
  same synchronisation from being stored.
- **Rationale:** The job synchronises seven days at a time. A single rejected day (the 25-hour day of
  2026-10-25) used to abort the whole run, so no price was stored for a week.
- **Source:** #516

## Impossible states

- Two prices of the same day stored at the same instant — prevented by PRC-001.

## Accepted exceptions

None.
