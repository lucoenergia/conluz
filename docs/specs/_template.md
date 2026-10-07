# Spec — <Capability name>

| | |
| --- | --- |
| **Prefix** | `<PREFIX>` |
| **Status** | Draft \| Active |
| **Contract** | OpenAPI snapshot → tag `<Tag>`; operations `<operationId>`, `<operationId>` |
| **Related ADRs** | ADR-NNNN (`docs/architecture/adr/`), ... |
| **Last reviewed against code** | #<issue> |

## Purpose

<Two or three sentences: what this capability is for and which part of the domain it owns.>

## Glossary

| Term | Meaning |
| --- | --- |
| <term> | <definition as used in Conluz, citing the regulation if applicable> |

## Rules

<!--
One block per rule. Statements are normative (MUST / MUST NOT / MAY) and testable.
Describe behaviour, not implementation. Do not restate fields or types from the OpenAPI snapshot.
-->

### <PREFIX>-001 — <Short title>

- **Status:** Active
- **Rule:** <Normative statement.>
- **Rationale:** <Why this rule exists. This is the part that would otherwise be lost when the issue is closed.>
- **Source:** #<issue>, ADR-NNNN, <regulation reference if any>

<!-- Optional, only when the rule is not obvious from its statement -->
**Scenarios**

```gherkin
Given <state>
When <action>
Then <outcome>
```

### <PREFIX>-002 — <Short title>

- **Status:** Removed in #<issue>
- **Rule:** <Original statement, kept for reference.>
- **Rationale:** <Why it was removed.>
- **Source:** #<issue>

## Impossible states

<!--
States the domain forbids. They are listed so nobody adds validations or error paths for them.
Each entry names the rule or structure that makes the state unrepresentable.
-->

- <State> — prevented by <PREFIX>-NNN / <DB constraint / type>.

## Accepted exceptions

<!-- Every exception needs a written expiry condition. -->

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| <description> | <reason> | <condition> | #<issue> |

---

<!--
EXAMPLE (delete when using the template)

### SUP-001 — A supply never changes community

- **Status:** Active
- **Rule:** A supply MUST belong to exactly one community and MUST NOT be reassigned to another one.
- **Rationale:** Consumption history and sharing coefficients are scoped to the community; moving a
  supply would silently corrupt past allocations.
- **Source:** #<issue>

### SUP-002 — A supply may be linked to several plants

- **Status:** Active
- **Rule:** A supply MAY be associated with more than one CAU plant at the same time.
- **Rationale:** The regulation allows it; assuming one supply = one plant would force a migration later.
- **Source:** #<issue>
-->
