# Spec — Datadis supply sync

| | |
| --- | --- |
| **Prefix** | `DSS` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Supplies`; operation `syncDatadisSupplies`, and the scheduled job `DatadisSuppliesSyncDailyJob` |
| **Related ADRs** | — |
| **Last reviewed against code** | #562 |

## Purpose

How the supplies registered in a community get the data that only Datadis holds for them: the distributor, the
point type and the contract start date, which the Datadis consumption sync needs to request their consumption. This
spec covers which communities and supplies are synced, with which Datadis account, and what is written.

## Glossary

| Term | Meaning |
| --- | --- |
| Community configuration | The Datadis account (username, password, base URL) a community stores, and whether it is enabled. |
| Registered supply | An enabled supply of the community. A disabled supply is not synced. |
| Datadis entry | One supply as Datadis returns it for an owner. Datadis returns one entry per contract, so a CUPS may have several. |
| Forms of a CUPS | The 22-character CUPS and, when it ends in `0F`, its first 20 characters (SUP-006, SUP-007). |

## Rules

### DSS-001 — Each community is synced with its own Datadis account

- **Status:** Active
- **Rule:** The supplies sync MUST call Datadis with the token and base URL of the configuration of the supply's
  community, and MUST log in once per community and run.
- **Rationale:** Using another community's account returns nothing or the wrong data: Datadis only returns the
  supplies that account is authorised for.
- **Source:** #562

### DSS-002 — A failing community does not stop the others

- **Status:** Active
- **Rule:** A failure syncing one community MUST NOT prevent the sync of the others. The scheduled sync MUST log it
  at ERROR with the community id.
- **Rationale:** One bad configuration aborted the sync of every community.
- **Source:** #562

### DSS-003 — Only communities with an enabled configuration are synced

- **Status:** Active
- **Rule:** Communities without an enabled Datadis configuration MUST NOT be synced, and Datadis MUST NOT be called
  for them. The per-community sync MUST reject them with the error the per-community consumption sync returns for
  the same case.
- **Rationale:** The same behaviour as the consumption sync.
- **Source:** #562

### DSS-004 — `authorizedNif` is decided against the supply's community account

- **Status:** Active
- **Rule:** Whether `authorizedNif` is sent MUST be decided against the username of the configuration of the
  supply's community, in every Datadis call: it is omitted only when the supply's owner holds that account.
- **Rationale:** The holder of a community's account is only the holder within that community.
- **Source:** #562

### DSS-005 — A Datadis entry matches a registered supply in either form and case

- **Status:** Active
- **Rule:** A Datadis entry MUST match a registered supply of the community whatever the form (20 or 22
  characters) and the case of the CUPS on either side.
- **Rationale:** The platform accepts both forms (SUP-006, SUP-007), and codes stored before #532 may be in lower
  case.
- **Source:** #562

### DSS-006 — A registered supply Datadis does not return is reported

- **Status:** Active
- **Rule:** A registered supply that Datadis did not return in the run MUST be logged at WARN with its supply id and
  community id, and MUST NOT be modified. A Datadis entry that matches no registered supply MUST NOT be logged on
  its own and MUST NOT modify data; only their number per community is logged, at DEBUG. Disabled supplies are
  excluded from the sync: they MUST NOT be matched, MUST NOT be updated, and MUST NOT produce the "not returned"
  WARN. No log line carries a CUPS.
- **Rationale:** Datadis returns every supply authorised for each owner, including supplies outside the community,
  so unmatched entries are expected every night. The actionable case is a registered supply Datadis does not return:
  a missing authorisation or a wrong CUPS. A disabled supply is one the community no longer manages, so Datadis not
  returning it is expected and writing to it would be unwanted. The CUPS identifies a point of supply and stays out
  of logs, as in DHI-002.
- **Source:** #562

### DSS-007 — An ambiguous match is skipped

- **Status:** Active
- **Rule:** A Datadis entry that matches more than one registered supply MUST be skipped with an ERROR naming the
  community id and the ids of the matched supplies, and MUST NOT abort the sync.
- **Rationale:** Duplicates remain possible: SUP-007 has an accepted exception for concurrent writes, and rows
  written before #532 were never checked.
- **Source:** #562

### DSS-008 — No transaction is held during Datadis calls, and each supply is written on its own

- **Status:** Active
- **Rule:** The supplies sync MUST NOT hold a database transaction while it calls Datadis, and MUST write each
  supply in a transaction of its own. A supply that cannot be written MUST be logged at ERROR with the community id
  and the supply id, and the others MUST still be written.
- **Rationale:** A late failure must not undo successful updates, and a transaction must not stay open while
  Datadis answers.
- **Source:** #562

### DSS-009 — The latest contract wins

- **Status:** Active
- **Rule:** When Datadis returns several entries for a CUPS, the entry with the most recent contract start date MUST
  be used. An entry without a start date, or with one that is not a date, ranks after every dated entry. Entries
  with the same start date are ordered by distributor code, then point type, then distributor name, then address,
  each in ascending order with a missing value last. When the entry used has no start date, or one that is not a
  date, the supply's stored contract start date MUST be kept.
- **Rationale:** Consumption requests must match the current contract. The tie-break covers every field the sync
  writes, so the result never depends on the order Datadis returns the entries in. A missing date from Datadis says
  nothing about the contract, so it must not erase a date already known.
- **Source:** #562

### DSS-010 — An address entered by the community admin is kept

- **Status:** Active
- **Rule:** The sync MAY write the supply address only when the stored address is blank. The distributor, the point
  type and the contract start date are written from Datadis on every run.
- **Rationale:** The community admin's edit takes precedence.
- **Source:** #562

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Addresses replaced with Datadis' address by runs before #562 are not restored. | The address the community admin had entered was overwritten and is not kept anywhere. | The community admins re-enter those addresses. | #562 |
