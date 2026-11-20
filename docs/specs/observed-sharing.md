# Spec — Observed sharing

| | |
| --- | --- |
| **Prefix** | `OBS` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Sharing agreements`; operations `getPlantSharingObservation`, `getSharingAgreementObservation` |
| **Related ADRs** | — |
| **Last reviewed against code** | #607 |

## Purpose

The registered sharing agreements are the source of truth of how a plant's production is shared; Datadis is the
only independent evidence of what the distributor applies. Observed sharing compares the two, day by day, to tell
admins when the distributor started applying each registered coefficient, when a supply stopped receiving energy
because it left the distribution, and where the distributor applies something no agreement registers. It only
observes: it never writes, and never proposes a coefficient.

## Glossary

| Term | Meaning |
| --- | --- |
| E | A consumer supply's self-consumption plus surplus over a local day: the share of the plant's production it received. |
| Plant energy (G) | The plant supply's energy over a local day: the energy the distributor shares. Read from its production records on and after the local day of its first production record, and from its legacy surplus on the days before it; never from both for one day. |
| Legacy surplus | The plant supply's `surplus_energy_kwh` in the consumption measurement, where its production was stored before it was stored as production. The Datadis sync stores it as 0 from the first production record on. |
| Plant hours | The plant supply's records over a local day in that day's source of G, zeros included: production records, or legacy surplus records. |
| Observed β | E / G on a valid day. |
| Observed β (median) | The median of a supply's observed β over a set of its valid days: the middle value of an odd count, the mean of the two middle values of an even one. |
| Valid day | A local day on which the consumer supply's published records and the plant hours both reach the daily form of ENM-001's publication threshold, and G ≥ max(0.5 kWh × the plant's peak power in kWp, 12 kWh). |
| Agreements in scope | The plant's PUBLISHED and SUPERSEDED agreements. |
| Candidate | A value a supply's observed β may match: one of its coefficient rows in an agreement in scope (an agreement may hold several rows for one supply), or the implicit 0 of an agreement it has no row in. |
| Match | A candidate within ±0.1 pp of the observed β. |
| Run | N consecutive valid days matching one candidate and not the candidate observed applied immediately before. N is configured (`conluz.sharing-observation.consecutive-days`, default 3). |
| Ambiguous day | A valid day that matches both the candidate observed applied and another candidate of a different value. For that other candidate it counts as a gap: it neither extends nor breaks its run. |
| Observed date | The first day of a run: exact; a range when non-valid or ambiguous days precede it; or open ("on or before") when it is the supply's first valid day. Dates are local days of the application time zone. |
| Registered order | The agreements ordered by their distributor file's sent date, else the earliest stored start of their rows as a local day; then by creation instant; then by id. |
| Leaving row | A supply absent from an agreement, after the agreement observed applied before it gave the supply a non-zero value. |
| Applied window | The days an agreement is taken as applied to the plant: from its most frequent observed application date to the day before the next agreement's in that order, or to the end of the analysed range. An agreement none of whose coefficient rows was observed has none, nor one whose window would end before it starts. 1 − Σβ is computed over it. |
| Share without data | The share an agreement registers for the supplies without a valid day on each plant-valid day of its applied window, the plant's own supply always among them (OBS-011), weighted by G (`registeredShareWithoutData`). Set next to 1 − Σβ, it tells how much of the production no supply with data received is explained. |
| Row window | The days a coefficient or leaving row is taken as applied to its supply: from the row's observed application or leaving date (the end of a range, or the day of an open date) to the end of its agreement's applied window; the whole applied window when the row has no observed date. None when the agreement has no applied window or the row's start falls after its end. |

## Rules

### OBS-001 — Observed β is computed on valid local days, and a coefficient is dated by its first run

- **Status:** Active
- **Rule:** G and the plant hours MUST come from the plant supply's production records, and from its legacy surplus
  records on the days before the local day of its first production record, never from both for one day. A day MUST
  be plant-valid only when its plant hours, from that day's source, reach the daily form of ENM-001's threshold. The
  analysed range MUST run from the local day of the plant supply's first record in either source to the last day of
  the latest of the 24 complete months before the current one whose plant hours reach ENM-001's monthly threshold.
  Consumers' energy and coverage MUST come from their consumption records. Observed β MUST be computed only on valid
  days, as E / G. A coefficient row's observed application date
  MUST be the first day of the first run of its candidate: a range spanning the non-valid and ambiguous days that
  precede the run, or open ("on or before") when the run starts on the supply's first valid day. Fewer than N
  matching valid days MUST NOT date anything. An agreement holding several rows for one supply MUST be matched row
  by row. Stored dates MUST be compared with observed ones as local days of the application time zone, so a start
  stored at 00:00 UTC of the observed day is the same day. When several candidates complete a run on the same day,
  the one not yet observed for the supply MUST be chosen first, then the earliest in registered order.
- **Rationale:** A plant supply's records are production only: it never carries self-consumption, and its earlier
  production was stored as surplus in the consumption measurement (#604). The sync zeroes that surplus once it writes
  production, so the two sources never carry the same energy, and the cutover keeps a day on one source. Runs make the date robust to a day's noise and to Datadis revisions; ranges and open dates say
  exactly what the data cannot tell, rather than guessing a day inside a gap or before the history starts (Datadis
  serves only two years). Ambiguous days arise when two values are closer than twice the tolerance: they are
  evidence for neither. The 12 kWh floor keeps the rounding of a day's 24 three-decimal Datadis values within the
  ±0.1 pp tolerance on any plant: an 8 kWp plant would otherwise accept 4 kWh days, with up to 0.3 pp of rounding
  error.
- **Source:** #579, #604, ENM-001

### OBS-002 — A coefficient or leaving row is in one status

- **Status:** Active
- **Rule:** A coefficient row's stored date is its `validFrom`; a leaving row's is the `validTo` of the supply's
  row in the previous agreement. A coefficient row of the plant's own supply MUST be `NOT_OBSERVABLE` (OBS-011).
  Any other row MUST be:
  - `NO_DATA` when the supply has no valid day;
  - `APPLIED_SAME_DATE` when observed and its stored date, as a local day, is the observed day, lies within the
    observed range, or is on or before an open date;
  - `APPLIED_DIFFERENT_DATE` when observed and its stored date differs or is missing;
  - `NOT_DISTINGUISHABLE` as OBS-003 states;
  - `PENDING_APPLICATION` when not observed and the agreement records a sent date;
  - `NOT_OBSERVED` otherwise.
- **Rationale:** An admin needs each coefficient's state to know which dates to set and which agreements the
  distributor has not applied yet. The plant's own supply never has a valid day, but its row is not missing data:
  its share cannot be measured at all, so `NO_DATA` would suggest a gap that later data could fill.
- **Source:** #579, #607

### OBS-003 — A coefficient equal to the previously observed one is not distinguishable

- **Status:** Active
- **Rule:** A coefficient row not observed MUST be `NOT_DISTINGUISHABLE` when its value is within ±0.1 pp of the
  supply's observed value in the latest agreement before it in registered order that was observed applied for the
  supply; a leaving row not observed, when that previous value is itself within ±0.1 pp of 0. Its proposed date MUST
  be the most frequent observed application date of the agreement's coefficient rows, ties going to the earliest,
  and none when nothing of the agreement was observed.
- **Rationale:** A change to the same value is not observable in the data, so its date can only be proposed from
  the rows of the same agreement that changed. Registered order uses the sent date first because most agreements
  are published without stored dates: ordering them by start, then creation, would put a synthetic agreement first
  and the rest in import order.
- **Source:** #579

### OBS-004 — Anomalies are sustained, merged and attributed

- **Status:** Active
- **Rule:** A supply absent from an agreement MUST be matched against 0 in it. A valid day matching no candidate
  MUST be anomalous as `NO_ENERGY_IN_AGREEMENT` when β is within ±0.1 pp of 0, `UNKNOWN_COEFFICIENT` when the
  supply has a non-zero value in some agreement in scope, and `ENERGY_OUTSIDE_AGREEMENT` otherwise; but before the
  supply's first observation no day with β within ±0.1 pp of 0 is anomalous. Consecutive anomalous valid days of one
  supply and type MUST be one anomaly when they last N days, with a start (date, range or open) and an end (its last
  day, or open when it lasts until the end of the data). A run of an agreement after a later run of a different
  agreement whose first run started after this one's first run MUST be `REVERTED`. An application or leaving whose
  observed date (the end of its range) is before the agreement's sent date MUST be `APPLIED_BEFORE_SENT`.
  `APPLIED_BEFORE_SENT` MUST be attributed to that agreement; every other anomaly to the agreement observed applied
  for the supply when it starts, or to none. A change to an agreement not yet observed for the supply MUST be an
  application or a leaving, never an anomaly. An anomaly's id MUST be derived from its supply, type and first day,
  so it is the same on every read of unchanged data.
- **Rationale:** The registered agreement is the source of truth: what departs from it is an anomaly, but a new
  agreement being applied is not. Merging prevents a flood of one-day anomalies, and stable ids let a client refer
  to one across reads. A supply is not yet in the distribution before it is first observed, so receiving nothing
  then is expected.
- **Source:** #579

### OBS-005 — Recommendations never propose coefficients

- **Status:** Active
- **Rule:** Each agreement MUST receive, in this order, every recommendation that applies: `SET_DATES` when a
  coefficient or leaving row is `APPLIED_DIFFERENT_DATE` or `NOT_DISTINGUISHABLE`; `DEACTIVATE` when no coefficient
  and no leaving row was observed and it records no sent date; `REVIEW_ANOMALIES` when anomalies are attributed to
  it; `NONE` when none applies. The analysis MUST NOT propose coefficient values.
- **Rationale:** Datadis evidences dates and anomalies; it cannot tell which registered value is right, only that
  data and registration disagree. An agreement never observed and never sent, like the synthetic agreement of the
  pre-model coefficients, is not in force.
- **Source:** #579

### OBS-006 — Only agreements in force are analysed, and what cannot be computed is said

- **Status:** Active
- **Rule:** Only the plant's PUBLISHED and SUPERSEDED agreements MUST be analysed; `getSharingAgreementObservation`
  on a DRAFT agreement MUST be refused with 409 and nothing computed. The plant's own supply MUST NOT be analysed as
  a consumer, nor any plant supply of the community. When the plant supply has no record in either source of G, or
  no month of the analysed range's window whose plant hours reach ENM-001's monthly threshold, the result MUST say the
  plant is not computable, with nothing observed. A supply holding
  coefficients in this plant and in another one MUST be reported as not computable and left out of Σβ. A supply
  holding coefficients only in another plant MUST be left out of the plant's analysis entirely: it is not listed and
  its energy is not counted.
- **Rationale:** Drafts are not in force. The energy of a supply shared by two plants cannot be attributed to one
  of them. A supply of another plant only is part of that plant's analysis; analysing it here would report the
  other plant's production as energy outside the agreements. A silent estimate would look like an observation. A plant
  supply is judged on where its production is stored (OBS-001), never on self-consumption it never carries.
- **Source:** #579, #604, PLT-002, ENM-001

### OBS-007 — The analysis is read-only, admin-only, and one computation

- **Status:** Active
- **Rule:** Observed sharing MUST be computed on every read and MUST NOT write anything. Both operations MUST be
  readable only by community admins of the plant's community: a member gets 403, and a caller who cannot see the
  plant (an admin of another community, a platform admin who is not a member) gets 404. The result of
  `getSharingAgreementObservation` MUST be exactly the agreement's part of `getPlantSharingObservation`. For a plant
  of 32 supplies with data since 2024-02, the plant operation MUST answer in under 5 s.
- **Rationale:** The analysis exposes every supply's daily energy, which is admin-only data. One computation sliced
  per agreement keeps a single source of truth. Computing on read keeps it consistent with the latest Datadis data
  and stored dates without a persistence layer.
- **Source:** #579

### OBS-008 — A supply leaving the distribution is a leaving row

- **Status:** Active
- **Rule:** A supply absent from an agreement MUST have a leaving row in it when the agreement observed applied
  before it for the supply (or, when the leaving is not observed, the latest agreement before it in registered order
  observed for the supply) gave it a non-zero value. Its observed leaving date MUST be the first day of the first run
  of the agreement's implicit 0, with the range and open-date rules of OBS-001, and its status MUST compare it with
  the stored `validTo` of the supply's row in that previous agreement. A supply never observed with a non-zero value
  before MUST NOT have a leaving row.
- **Rationale:** A supply that left must stop receiving attributed production on the right date; the stored end of
  its last coefficient is what records it.
- **Source:** #579, #593

### OBS-009 — Every row and anomaly reports the observed β it rests on

- **Status:** Active
- **Rule:** Every coefficient row and every leaving row MUST report the observed β (median) of its supply over its
  valid days in its row window, and the number of those days. Every anomaly MUST report the observed β (median) of
  its supply over its own valid days, from the last day of its start (the end of a range) to its end, or to the
  supply's last valid day when it is open, and the number of those days. The value MUST be on the 0-1 scale of the
  coefficients, rounded half-up to 6 decimals. It MUST be null, over 0 days, when the row has no row window or the
  supply has no valid day in it. It MUST be computed from the days the analysis already read, in the
  same computation, and MUST NOT change any status, date, anomaly, recommendation or 1 − Σβ.
- **Rationale:** Admins must be able to verify every status and anomaly against the data before acting on it. A row
  observed on its own date starts there: over the whole applied window, a row applied late would report the previous
  coefficient's value whenever its delay exceeded half the window, which reads as a false mismatch. The window still
  ends where the agreement's does, and a row never observed keeps the whole applied window, since it has no date of
  its own. The median keeps a few noisy or revised days from moving the value.
- **Source:** #597

### OBS-010 — Every coefficient row reports its stored period

- **Status:** Active
- **Rule:** Every coefficient row MUST report its stored `validFrom` and its stored exclusive `validTo` as local
  days of the application time zone, each null when not stored (a pending start, an open end), so a value stored
  at 00:00 UTC is reported as that local day.
- **Rationale:** The registered period is shown next to the observation without a second request.
- **Source:** #597

### OBS-011 — A plant supply's coefficient is listed, never observed

- **Status:** Active
- **Rule:** Each coefficient row the plant's own supply holds in an agreement in scope MUST be listed among the
  agreement's coefficient rows as `NOT_OBSERVABLE`, with its stored period (OBS-010), no observed date, no proposed
  date, and an observed β of null over 0 days, and MUST be counted in the agreement's coefficient status counts. Its
  registered value MUST count in the agreement's share without data on every plant-valid day of the applied window,
  as for a supply without data: its only row in the agreement, or the row whose stored period holds the day. Such a
  row MUST NOT count as observed, MUST NOT trigger `SET_DATES` or any other recommendation, and MUST NOT change 1 − Σβ.
  The plant's own supply is still never analysed as a consumer (OBS-006): it has no β, no run, no anomaly and no
  leaving row. A plant that is not computable lists no row, this one included (OBS-006).
- **Rationale:** The plant's own supply can hold a coefficient, but its Datadis surplus is the plant's production
  and it has no self-consumption, so its share cannot be measured. Left out, its registered share appeared as
  production no one received, unexplained. Its application date is set by hand, from the distributor file. The share
  without data follows the same row choice as for every other supply without data, because most rows are published
  without stored dates.
- **Source:** #607, PLT-002

## Impossible states

- An open leaving date — prevented by OBS-008: a leaving is observed only after a previous observation, so a valid
  day always precedes its run.
- A leaving row with status `NO_DATA` — prevented by OBS-008: it requires a previous observation, so the supply has
  valid days.
- An observed β on a plant that is not computable — prevented by OBS-006: such a result lists no coefficient row, no
  leaving row and no anomaly, and a supply that is not computable has none either, so nothing carries one.
- A leaving row with status `NOT_OBSERVABLE`, or a non-zero `notObservable` count among an agreement's leaving rows
  — prevented by OBS-006 and OBS-011: only the plant's own supply has `NOT_OBSERVABLE` rows, and it is never matched
  as a consumer, so it never leaves.
- A `NOT_OBSERVABLE` row with an observed date, a proposed date or an observed β — prevented by OBS-011: the plant's
  own supply has no timeline.
- An anomaly with a null observed β — prevented by OBS-004: every anomaly starts on a valid day of its supply, which
  is one of its own valid days.

## Accepted exceptions

None.
