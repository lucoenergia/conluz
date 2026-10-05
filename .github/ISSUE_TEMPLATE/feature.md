---
name: Feature
about: A change to what the app does
labels: 'enhancement'
---

<!-- This repository is public. Never paste a real NIF/personalId, CUPS code, hostname, token,
     password or any other credential or personal data. Use placeholders. -->

## Context

<!-- What is true today, and why that is a problem. -->

## Scope

<!-- What this covers, and what it deliberately does not. -->

## Authorization

**Mandatory.** Every endpoint carries one `@PreAuthorize` delegating to `@communityAccessGuard`,
the rule lives in a pure policy, and a client learns the decision from a capability on the resource
it concerns. Decide who may do this *before* it is built, not in review. See
`docs/security/authorization-policy.md` and `docs/security/capability-inventory.md`.

| Question | Answer |
| --- | --- |
| Who may see or do this | <!-- e.g. a community admin of the plant's community; the supply's owner --> |
| Guard method | <!-- e.g. `canManagePlant(#plantId)`, existing or new --> |
| Policy holding the rule | <!-- e.g. `PlantAccessPolicy#canManage`, existing or new --> |
| Denials | <!-- who gets 404 (cannot see the object) and who gets 403 (can see it, may not act) --> |
| Capability that reports it | <!-- e.g. `PlantResponse.capabilities.canManage`, or `SERVER_ONLY` and why --> |
| Does that capability exist yet | <!-- yes / no --> |
| `CapabilityInventory` entry | <!-- the existing row, or "new" --> |

If the honest answer is "any authenticated caller" (`isAuthenticated()`), say that and say why — it
is a decision, and it needs a reason that survives reading.

## Contract and data impact

<!-- New or changed endpoints, schemas, status codes or error codes, and whether the change is
     breaking for the generated client in `conluz-web` (a new required field is). Any Liquibase
     changeset or InfluxDB measurement change, and what happens to existing data. The
     `conluz-web` issue this needs, or "none". -->

## Tasks

- [ ] <!-- ... -->

## Acceptance criteria

- <!-- Observable, and checkable by someone who did not write the code. Include the denials:
       which personas get 401, 403 and 404. -->
