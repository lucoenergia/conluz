# Spec — Estimated tariff

| | |
| --- | --- |
| **Prefix** | `TRF` |
| **Status** | Active |
| **Contract** | No operation of its own: it prices the savings of every operation that returns `savings`, `savingsEur` or `savedEur` |
| **Related ADRs** | — |
| **Last reviewed against code** | #610 |

## Purpose

When a supply's contracted tariff is not known, its self-consumed energy is priced with an estimate: a configured
energy-term price plus the taxes a member pays on top of it. This spec owns how that estimate prices a kWh and how
the taxes in force on each day are chosen.

## Glossary

| Term | Meaning |
| --- | --- |
| Base price | The configured energy-term price per kWh, before taxes. |
| Electricity tax | The Impuesto Especial sobre la Electricidad (IEE), charged on the energy term before VAT. |
| Tax period | A configured rate of one tax, in force from a civil date (included) until another (excluded), or open-ended. |
| Default rate | The rate of a tax applied to a date no period of that tax covers. |

## Rules

### TRF-001 — A kWh is priced with the electricity tax, then VAT

- **Status:** Active
- **Rule:** An estimated kWh MUST be priced as `base × (1 + electricityTaxRate) × (1 + vatRate)`, with the
  electricity tax applied before VAT.
- **Rationale:** VAT is charged on the base plus the electricity tax; that is what the member actually avoids paying.
- **Source:** #610

### TRF-002 — The schedule splits at every tax boundary

- **Status:** Active
- **Rule:** The estimated schedule MUST split the range at every boundary of the VAT periods and of the
  electricity-tax periods, each segment carrying the rates in force on it.
- **Rationale:** Both taxes changed several times since 2024, on different dates.
- **Source:** #610

**Scenarios**

```gherkin
Given VAT changes on 2024-03-01 and the electricity tax on 2024-04-01
When savings are priced from 2024-02-01 to 2024-05-01
Then February, March and April are each priced with the rates in force on them
```

### TRF-003 — An uncovered date takes the default rate, and is logged

- **Status:** Active
- **Rule:** A date covered by no configured period of a tax MUST be priced at that tax's default rate, and the
  uncovered range MUST be logged at WARN, at most once per process for each tax and uncovered stretch (before the
  first period, or after a closed last period).
- **Rationale:** Never price at zero tax silently; make a missing new period visible. Daily and hourly series price
  one day at a time, so logging every request would repeat the same warning once per day.
- **Source:** #610

### TRF-004 — An invalid tax timeline stops startup

- **Status:** Active
- **Rule:** Overlapping, gapped or unordered periods, an open-ended period that is not the last, and a rate outside
  `[0, 1)` MUST stop the application from starting, naming the offending property.
- **Rationale:** A plausible-looking wrong amount is worse than a startup failure.
- **Source:** #610

### TRF-005 — The reported estimated price stays before taxes

- **Status:** Active
- **Rule:** `estimatedPrice.eurPerKWh` MUST remain the configured energy-term price before taxes.
- **Rationale:** It states what the estimate was based on, not what was charged.
- **Source:** #610

## Impossible states

- Two rates of one tax for the same date, or a date between two periods with no rate — prevented by TRF-004.
- A segment priced with rates from different days — prevented by TRF-002: no boundary falls inside a segment.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| VAT is the reduced regime for a contracted power of 10 kW or less, applied to every supply. | The contracted power of a supply is not known. Supplies above 10 kW are underpriced in reduced-VAT periods. | The contracted power of each supply is known. | #610 |
| Rates are applied by consumption date, not by billing date. | The billing date of a kWh is not known. | Contracted tariffs, with their billing periods, are priced. | #610 |
