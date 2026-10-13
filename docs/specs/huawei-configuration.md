# Spec — Huawei configuration

| | |
| --- | --- |
| **Prefix** | `HCF` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Production`; operations `getHuaweiConfig`, `configureHuawei` |
| **Related ADRs** | — |
| **Last reviewed against code** | #399 |

## Purpose

The credentials and settings a plant uses to reach the Huawei FusionSolar API. This spec covers
only how the stored password is set, kept and protected when the configuration is written; the
other fields and the sync jobs that use the configuration are not specified yet.

## Glossary

| Term | Meaning |
| --- | --- |
| Stored password | The FusionSolar password held in the plant's Huawei configuration. It is write-only: no operation returns it, only whether one is set. |
| Omitted password | A configuration write whose request does not carry the password field at all, as opposed to one carrying it with a `null` value. |

## Rules

### HCF-001 — An omitted password keeps the stored one

- **Status:** Active
- **Rule:** A write of a plant's Huawei configuration that omits the password MUST update the other
  fields and MUST keep the stored password unchanged, when the plant has a stored non-empty password.
- **Rationale:** The password is write-only, so a client cannot echo it back. Requiring it on every
  write forced an admin to retype it to change anything else, blocked edits when it was not at hand,
  and let a mistyped password silently overwrite a working one.
- **Source:** #399

### HCF-002 — A password is required when there is none to keep

- **Status:** Active
- **Rule:** A write of a plant's Huawei configuration that omits the password MUST be rejected with
  `400` when the plant has no stored non-empty password: either it has no configuration, or its
  configuration holds a `null` or empty password. Nothing is stored or changed.
- **Rationale:** A configuration without credentials cannot connect. Configurations written before
  #399, when the request body was not validated, may hold a `null` or empty password; keeping that
  "password" would preserve a configuration without credentials instead of asking for them.
- **Source:** #399

### HCF-003 — An explicit null password is rejected

- **Status:** Active
- **Rule:** A write of a plant's Huawei configuration whose password is explicitly `null` MUST be
  rejected with `400`, whether or not the plant has a configuration. Nothing is stored or changed.
- **Rationale:** Clearing credentials has no use case yet. Rejecting `null`, rather than reading it
  as "omitted" or as "clear", keeps a client that mis-serialises an empty field from silently wiping
  working credentials, and leaves `null` free to mean "clear" later without breaking any client.
- **Source:** #399

## Impossible states

- A configuration written from #399 on with no password — prevented by HCF-002 and by the contract
  rejecting an empty password. No validation of "enabled without a password" is therefore added.

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Configurations stored before #399 may hold a `null` or empty password | Request bodies were not validated before #399 | The configuration is next written: HCF-002 then requires a password | #399 |
