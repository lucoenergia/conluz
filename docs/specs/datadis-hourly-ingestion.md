# Spec — Datadis hourly ingestion

| | |
| --- | --- |
| **Prefix** | `DHI` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → written by `syncDatadisConsumptions` and by the scheduled job `DatadisSyncDailyJob`; read by `getSupplyHourlyConsumption`, `getDatadisHourlyProduction` and `getDatadisConsumptionHourlyCsvReport`, among others |
| **Related ADRs** | — |
| **Last reviewed against code** | #562 |

## Purpose

How the hourly records Datadis returns for a supply become points of the hourly consumption series and,
for plant supplies, of the hourly production series derived from them. This spec covers which supplies are
synced, how the sync logs in to Datadis, and the instant each record is stored at; the rest of the capability is specified lazily, as issues
touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Datadis hourly record | One row of a supply's hourly data as Datadis returns it: a local calendar date and a time label, with no offset and no UTC instant. |
| Time label | The local time at which the record's hour ends, from `01:00` to `24:00`. `24:00` is the midnight that ends the day. |
| Local calendar day | A calendar day in the time zone the application is configured with. It lasts 23 hours when the clocks go forward, 25 hours when they go back, and 24 hours otherwise. |

## Rules

### DHI-001 — A complete day is placed by position from its start

- **Status:** Active
- **Rule:** When a supply's Datadis records for one local calendar day number exactly the hours of that
  day in the configured time zone (23, 24 or 25), the n-th record, ordered by its time label with ties
  kept in the order Datadis returned them, MUST be stored at the start of that day plus (n − 1) hours.
- **Rationale:** Datadis labels the end of the hour, with no offset. On the day the clocks go back it
  repeats `03:00` (the end of 02:00–03:00 in summer time, then in winter time), and on the day they go
  forward it omits `03:00`. Reading the label as a local time stored every record one hour late, and
  resolved both October `03:00` records to the same instant, so one overwrote the other. Only the
  elapsed time from the day's start gives every hour its own instant, the same approach as PRC-001.
- **Source:** #322

**Scenarios**

```gherkin
Given the 25 records Datadis returns for 2025/10/26, labelled 01:00, 02:00, 03:00, 03:00, 04:00 … 24:00
When they are stored with Europe/Madrid as the configured time zone
Then 25 distinct points are stored, hourly, from 2025-10-25T22:00Z to 2025-10-26T22:00Z
And the first 03:00 record is stored at 00:00Z and the second at 01:00Z
```

### DHI-002 — Any other day is placed by label and reported

- **Status:** Active
- **Rule:** Otherwise each record MUST be stored one hour before the local instant its label names
  (`24:00` being the midnight that ends the day), and the sync MUST log one warning per such day with
  the supply id, the date, and the found and expected counts. It MUST NOT log the CUPS.
- **Rationale:** Datadis labels the end of the hour, repeats `03:00` in October and omits it in March.
  Placing records by position on an incomplete day would shift every hour after the gap. The label is
  correct on any day without a transition, and a missing hour is usually a meter fault, so the day is
  kept rather than rejected. The warning makes the gap visible; the CUPS identifies a point of supply and
  stays out of logs.
- **Source:** #322

### DHI-003 — Production is stored at the instants of the consumption it derives from

- **Status:** Active
- **Rule:** Hourly production derived from Datadis records MUST be stored at the same instants as the
  hourly consumption of those records.
- **Rationale:** For a plant supply both series come from the same records and are compared hour by
  hour, so they are placed by a single resolution of the records' instants.
- **Source:** #322

### DHI-004 — A supply without distributor code or point type is skipped once per run

- **Status:** Active
- **Rule:** The consumption sync MUST skip a supply that lacks a distributor code or a point type, and MUST log it
  once per run, at WARN, with the supply id and the community id, never the CUPS. Datadis MUST NOT be called for it.
- **Rationale:** Missing configuration is not a per-month error: both values are filled by the Datadis supply sync
  ([`datadis-supply-sync.md`](datadis-supply-sync.md)), and a supply waiting for it logged one ERROR per month of
  the window every night, which drowned real alerts.
- **Source:** #562

### DHI-005 — One Datadis login per community and run

- **Status:** Active
- **Rule:** A consumption sync of a community MUST log in to Datadis at most once with that community's
  configuration, and MUST send that token on every consumption request of the run, across supplies and months; the
  sync of a single supply follows the same rule, one login per call. When Datadis rejects a consumption request
  with 401, the sync MUST log in once more for that community, retry that request once with the new token, and use
  the new token for the rest of the run. A second 401 for the same request MUST fail that month of that supply,
  logged at ERROR with the supply id, and the run MUST continue.
- **Rationale:** The sync logged in once per supply and month: hundreds of logins per community every night, each
  one a request Datadis counts. A token can still expire during a long run, and one renewal recovers it without
  returning to a login per request; a token rejected again right after a new login is not an expiry, so it is
  not retried further.
- **Source:** #562

### DHI-006 — A community without an enabled Datadis configuration is refused

- **Status:** Active
- **Rule:** The consumption sync of a community that has no Datadis configuration, or whose configuration is
  disabled, MUST be refused with 409 and the `error.datadis.disabled` message, which names the operation that
  enables the configuration. The refusal MUST come before any supply is read and before any request to Datadis.
  It applies to the sync of the whole community and of a single supply.
- **Rationale:** Without an enabled configuration there is no Datadis account to call with, and the community admin
  has turned the integration off or never set it up. A 409 names the state to change, where an empty success would
  hide that nothing was synced. The supplies sync answers the same case the same way (DSS-003).
- **Source:** #142 added the enable flag and the refusal; #414 made the message name the enable operation; #562
  declared the 409 in the contract.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Hourly records older than the hourly re-sync window ([`datadis-pre-aggregation.md`](datadis-pre-aggregation.md)) when #322 was deployed remain stored at the end of their hour unless re-synced manually. | The fix applies to records as they are synced; the daily sync rewrites only the window, and an in-place shift of stored points is not idempotent and cannot recover the October hours already overwritten. | Those months are re-synced manually, or are no longer kept. | #322 |
