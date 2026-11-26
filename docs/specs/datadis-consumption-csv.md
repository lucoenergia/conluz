# Spec — Datadis consumption CSV exports

| | |
| --- | --- |
| **Prefix** | `DCX` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operation `getSupplyDatadisConsumptionHourlyCsv` |
| **Related ADRs** | — |
| **Last reviewed against code** | #636 |

## Purpose

A Datadis consumption CSV hands the hourly records Datadis delivered, as Conluz stored them, to a member or an admin
who analyses them outside Conluz, typically in a spreadsheet. It computes nothing from them. This spec covers the
per-supply monthly export; the community hourly report (`getDatadisConsumptionHourlyCsvReport`) is not covered yet.

## Glossary

| Term | Meaning |
| --- | --- |
| Stored record | One point of a supply's hourly Datadis consumption, stored at the instant its hour starts (DHI-001). A field Datadis did not deliver is absent from it. |
| Datadis label | The local time Datadis gives a record: the end of its hour, `01:00` to `24:00`. It is not stored. |
| Local start of the hour | The local date and time, in the time zone the application is configured with, at which a stored record's hour starts: `00:00` to `23:00`. |

## Rules

### DCX-001 — The file is a Spanish-spreadsheet CSV of the values as stored

- **Status:** Active
- **Rule:** A Datadis consumption CSV MUST be UTF-8 with a byte order mark, `;` separated, with CRLF line ends, with
  the Datadis field names as header. Every value Datadis delivered MUST be written as stored: text unchanged, a
  number unrounded, as the shortest decimal that identifies the stored value, in plain notation with a decimal comma,
  and a null as an empty field. A text field holding `;`, `"`, CR or LF MUST be quoted, with its quotes doubled. The
  `date` and `time` columns MUST be the local start of the record's hour, not the Datadis label, which names the end
  of the hour (`01:00` is 00:00 to 01:00).
- **Rationale:** A spreadsheet in a Spanish locale opens the file as it is, without altering Datadis data. Datadis
  labels are not stored (DHI-001), so the hour can only be named by when it starts, as every hourly read names it.
- **Source:** #636

### DCX-002 — Lines follow the stored instants and carry the hourly series' date and time

- **Status:** Active
- **Rule:** `date` and `time` MUST be the local date and start time of the record's hour, identical to those
  `getSupplyHourlyConsumption` returns for the same hour, and lines MUST be in chronological order of the stored
  instant. On the day the clocks go back, the repeated hour appears as two consecutive lines with the same `date` and
  `time`, earlier instant first. On the day the clocks go forward, the skipped hour has no line.
- **Rationale:** One derivation for the same hour across operations. Datadis's hour-ending labels are not stored
  (DHI-001), and on the day the clocks go back a local date and time names two hours: only the order tells them apart.
- **Source:** #636

**Scenarios**

```gherkin
Given a supply with a record for every hour of 2026-10-25 in Europe/Madrid
When its 2026-10 CSV is downloaded
Then that day has 25 lines
And two consecutive lines carry 2026/10/25 and 02:00, the first with the values of the hour in summer time
```

### DCX-003 — One calendar month, the current one at the latest

- **Status:** Active
- **Rule:** A Datadis consumption CSV MUST cover exactly one calendar month, given as `month=YYYY-MM`: the records whose
  hour starts from the first day of the month at 00:00 up to, not including, the first day of the next month at
  00:00, in the application's time zone. A month after the current one in that zone MUST be rejected with
  `CONSUMPTION_CSV_MONTH_IN_FUTURE`. The current month MAY be requested. There is no earliest month.
- **Rationale:** A month bounds the size of the file. Half-open local bounds follow the period convention (EPR-001), so
  each hour belongs to exactly one month. No record can exist for a later month.
- **Source:** #636

### DCX-004 — Only stored records are written

- **Status:** Active
- **Rule:** An hour without a stored record MUST NOT be written. A month without records MUST return the header line
  only.
- **Rationale:** The export is raw: an absent hour is information, which a filled-in hour would hide.
- **Source:** #636

### DCX-005 — The supply's export is read as its hourly series is

- **Status:** Active
- **Rule:** `getSupplyDatadisConsumptionHourlyCsv` MUST be readable exactly by the callers who can read the supply's
  hourly series (`getSupplyHourlyConsumption`), each answered with the same status. A disabled supply's records MUST
  still be written: the export MUST NOT drop any stored record of the month because the supply is disabled.
- **Rationale:** One authorisation rule for the same data. A supply's history stays its owner's after the supply is
  disabled.
- **Source:** #636

## Impossible states

- Two stored records of one supply at the same instant: the measurement's only tag is the CUPS, so a record written
  again at the same instant is written to the same point. It overwrites the fields it carries and keeps the others.

## Accepted exceptions

None.
