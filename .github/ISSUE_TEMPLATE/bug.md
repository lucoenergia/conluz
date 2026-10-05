---
name: Bug
about: Something behaves incorrectly
labels: 'bug'
---

<!-- This repository is public. Never paste a real NIF/personalId, CUPS code, hostname, token,
     password or any other credential or personal data, here or in a request/response sample.
     Use placeholders. -->

## Problem

<!-- What happens, and what should happen instead. -->

## How to reproduce

<!-- The persona matters: a member, a community admin, a platform admin, or a caller with no
     community get different answers, and most authorization bugs only appear for one of them. Say
     which, and which community the target belongs to. Give the endpoint and the request (method,
     path, body), with placeholder values. -->

1. <!-- ... -->

## Authorization

**Mandatory for anything about what a caller can read or do.** Write "not an authorization bug" if
it is genuinely unrelated.

| Question | Answer |
| --- | --- |
| Persona that sees the bug | <!-- member / community admin / platform admin / no community / anonymous --> |
| Endpoint and its guard | <!-- e.g. `PUT /api/v1/supplies/{supplyId}` → `@communityAccessGuard.canEditSupply` --> |
| Status expected and received | <!-- 401 / 403 / 404 / 409 / 2xx — see `docs/security/authorization-policy.md` --> |
| Capability that should report it | <!-- e.g. `SupplyResponse.capabilities.canEdit`, or "none" --> |
| Does the capability disagree with the guard | <!-- yes / no — e.g. `canEdit: true` but the call answers 403 --> |

## Severity

<!-- Does it expose another community's data, reveal that an object exists (a 403 where a 404 is
     due), or let a caller act outside their scope? Say so plainly: a defect that breaks users once
     a dependent change ships is a release blocker, not debt. -->
