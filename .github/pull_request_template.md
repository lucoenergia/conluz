# [conluz-000] Short imperative title

<!-- Title: the issue code in brackets, then what the PR does in the imperative mood, under ~70
     characters. Use the code of the issue this PR closes: `[conluz-292]` for an issue in this
     repository. Use the same line as the PR title, so the squash commit on main carries the code
     too. -->

Closes #000

<!-- Closes: the issue(s) this PR resolves, one GitHub closing keyword per line, so merging closes
     them: `Closes #123`, or `Closes lucoenergia/conluz-web#123` for an issue in the frontend
     repository. If the PR only contributes to an issue without finishing it, write `Part of #123`
     instead, which links it without closing it. -->

<!-- This repository is public. Never write a real hostname, filesystem path, community name, CUPS
     code, NIF/personalId or any credential in this description — not even in a SQL query or a
     curl sample. Those belong to `conluz-infra`. Use placeholders. -->

## Summary

<!-- Summary: the behaviour that is different after this PR, and the problem it solves, in two or
     three sentences. Written for someone who has not read the issue. Not a list of files. -->

## Changes

<!-- Changes: what was done, grouped by concern rather than by file (e.g. "Domain", "Endpoint",
     "Persistence", "Authorization", "Docs"). One bullet per meaningful change, saying what and
     why. Call out anything that is a pure refactor, so reviewers know which part carries
     behaviour. -->

-

## Authorization

<!-- Authorization: mandatory. Delete no row — write "none" and why, if that is the answer.
     All access decisions live in the controller's `@PreAuthorize`, the rule itself in a pure
     policy under `domain/admin/community/access/policy`. If this PR adds or changes an endpoint,
     a guard, a policy or a capability, say so here. -->

| Question | Answer |
| --- | --- |
| Guard method added or changed | <!-- e.g. `canManagePlant(#plantId)`, or "none" --> |
| Policy added or changed | <!-- e.g. `PlantAccessPolicy#canManage`, or "none" --> |
| Denial mapping | <!-- who gets 401 / 403 / 404 on each new or changed endpoint --> |
| Capability added or changed | <!-- e.g. `PlantResponse.capabilities.canManage`, or "none" --> |
| `CapabilityInventory` entry | <!-- the guard → capability mapping, `SERVER_ONLY` and why, or "none" --> |

- [ ] Every new handler method carries exactly one `@PreAuthorize`: `isAuthenticated()` or a single
      `@communityAccessGuard.<method>(...)` call — no `and`/`or`/`!`, no `hasRole(...)`.
- [ ] No access rule outside `..access.policy..`; services and repositories hold no
      access-control logic.
- [ ] Controller tests assert 401 (no token), 404 (caller cannot see the object) and 403 (caller
      can see it but may not act) for every new endpoint.
- [ ] Every new capability is assembled from the same policy as its guard, is covered by
      `CapabilityGuardEquivalenceTest`, and a listing builds it without a query per item.
- [ ] A listing returns no row the object's own `GET` would answer 404 on for the same caller.

## Contract impact

<!-- Contract impact: new or changed endpoints, schemas, status codes and error codes, and whether
     `src/test/resources/openapi/api-docs.json` was updated in the same commit. Say whether the
     change is breaking for the generated client (a new required field, a removed field, a
     flat-to-nested change). Write "none — snapshot unchanged" if there is none. -->

## Tests

<!-- Tests: which tests were added or changed and what each one proves — a table mapping each
     acceptance criterion to its test(s) works well. Show that new tests fail without the change
     (fail-then-pass), and that every new ArchUnit rule fails on a violation. Give the exact
     commands that were run with their result (pass counts, failures). If a check was skipped,
     say which and why. -->

```bash
./gradlew build
```

## Migration / data impact

<!-- Migration / data impact: Liquibase changesets added, InfluxDB measurement or retention
     changes, backfills, and what happens to existing rows. Include any read-only query an
     operator should run before deploying, with placeholders only. Write "none" if there is none.
-->

## Frontend impact

<!-- Frontend impact: what `conluz-web` has to do — regenerate the client, gate a new affordance on
     a capability, handle a new status or error code — and the `conluz-web` issue for it, or a
     ready-to-file issue text. Write "none — no contract change" if there is none. -->

## Findings

<!-- Findings: anything discovered while doing the work that is not the change itself — a bug
     found elsewhere, a stale doc, a wrong assumption in the issue. Say what was found and whether
     it was fixed here. A finding that is not fixed here gets a ready-to-file issue text (title +
     body) in this section, or the number of the issue already opened for it. Write "none" if
     there were none. -->

## Risks & follow-ups

<!-- Risks & follow-ups: what could break or behave differently in production (other endpoints
     sharing the touched code, query cost, transaction boundaries, a dependency on a frontend
     release, data migrations), and how likely it is. Then the work deliberately left out of this
     PR, each with an issue number or a ready-to-file issue text — never "later" or "in the next
     PR". Also the assumptions and trade-offs the reviewer should push back on. -->

## Deviations

<!-- Deviations: where this PR departs from the issue — an acceptance criterion that does not
     apply, a decision taken differently, scope added or dropped — with the reason. Write "none"
     if there were none. -->

## How to verify

<!-- How to verify: numbered, reproducible steps a reviewer can follow against the running app
     (Swagger UI at https://localhost:8443/api-docs/swagger-ui/index.html, or curl) — which persona
     to authenticate as (member, community admin, platform admin), which request to send, and what
     status and body they should get. Include at least one step that shows what must NOT happen
     (e.g. a caller without the permission gets 403, or 404 if they cannot see the object). -->

1.
