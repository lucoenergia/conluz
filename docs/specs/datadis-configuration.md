# Spec — Datadis configuration

| | |
| --- | --- |
| **Prefix** | `DCF` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Consumption`; operations `getDatadisConfig`, `configureDatadis` |
| **Related ADRs** | — |
| **Last reviewed against code** | #404 |

## Purpose

The credentials and settings a community uses to reach the Datadis API. This spec covers only how
the stored password is set, kept and protected when the configuration is written; the other fields
and the sync jobs that use the configuration are not specified yet.

## Glossary

| Term | Meaning |
| --- | --- |
| Stored password | The Datadis password held in the community's Datadis configuration. It is write-only: no operation returns it, only whether one is set. |
| Omitted password | A configuration write whose request does not carry the password field at all, as opposed to one carrying it with a `null` value. |

## Rules

### DCF-001 — An omitted password keeps the stored one

- **Status:** Active
- **Rule:** A write of a community's Datadis configuration that omits the password MUST update the
  other fields and MUST keep the stored password unchanged, when the community has a stored
  non-empty password.
- **Rationale:** The password is write-only, so a client cannot echo it back. Overwriting it on every
  write meant that saving any other field without retyping the password silently wiped working
  credentials.
- **Source:** #404

### DCF-002 — A password is required when there is none to keep

- **Status:** Active
- **Rule:** A write of a community's Datadis configuration that omits the password MUST be rejected
  with `400` when the community has no stored non-empty password: either it has no configuration,
  or its configuration holds a `null` or empty password. Nothing is stored or changed.
- **Rationale:** A configuration without credentials cannot connect. Configurations written before
  #404, when the request body was not validated, may hold a `null` or empty password; keeping that
  "password" would preserve a configuration without credentials instead of asking for them.
- **Source:** #404

### DCF-003 — An explicit null password is rejected

- **Status:** Active
- **Rule:** A write of a community's Datadis configuration whose password is explicitly `null` MUST
  be rejected with `400`, whether or not the community has a configuration. Nothing is stored or
  changed.
- **Rationale:** Clearing credentials has no use case yet. Rejecting `null`, rather than reading it
  as "omitted" or as "clear", keeps a client that mis-serialises an empty field from silently wiping
  working credentials, and leaves `null` free to mean "clear" later without breaking any client.
- **Source:** #404

## Impossible states

- A configuration written from #404 on with no password — prevented by DCF-002 and by the contract
  rejecting an empty password. No validation of "enabled without a password" is therefore added.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Configurations stored before #404 may hold a `null` or empty password | Request bodies were not validated before #404 | The configuration is next written: DCF-002 then requires a password | #404 |
