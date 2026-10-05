# AGENTS.md

This file provides guidance to AI agents (e.g. Claude Code, opencode) when working with code in this repository.

## Testing

Tests use Testcontainers (PostgreSQL and InfluxDB), so Docker must be running for `./gradlew test`.

## Deployment & infrastructure boundary

This repository is **public and world-readable**. Environment-specific values — real
hostnames, filesystem paths, real service/community names, CUPS codes, backup schedules, and
any credential (JWT keys, DB/InfluxDB/MQTT passwords, `PGPASSWORD`, tokens) — **must never
enter this repo**. They live in the **private `conluz-infra` repository**, which is the single
source of truth for production topology and operational tooling (backups, restores, snapshots,
monitoring, reverse proxy, host configuration).

- `deploy/` here is a **sanitized reference example, not a mirror of any production setup**:
  `docker-compose.example.yml` + `.env.example` use `${VAR}`/placeholder values only, plus the
  two generic DB init scripts.
- Real secret values live only in gitignored `.env` files on the host; committed files use
  `${VAR}` interpolation and `*.env.example` templates. `.env`, `*.env`, `*.key`, `*.pem` are
  gitignored (but `*.env.example` is allowed).
- A **gitleaks `pre-commit` hook** is the backstop — install it once per clone
  (`pre-commit install`); see `docs/gitleaks.md`. If a real secret is ever found committed
  here, treat it as **compromised**: rotate it (a human decision), do not just delete the file
  (deletion does not remove it from history).

## InfluxDB Schema

The time-series database stores consumption, production and price data in the measurements described in
[`docs/db/timeseries/influxdb/influxdb_schema.md`](docs/db/timeseries/influxdb/influxdb_schema.md), with
`docs/db/timeseries/influxdb/influxdb_schema.txt` holding the complete schema with sample data.

## Configuration

### Required Environment Variables

- `CONLUZ_JWT_SECRET_KEY`: JWT secret key (≥256 bits, HMAC-SHA compatible). Generate using `org.lucoenergia.conluz.infrastructure.shared.security.JwtSecretKeyGenerator`

### Database Setup

For new installations and existing databases (PostgreSQL and InfluxDB setup scripts), see
[`docs/db/setup.md`](docs/db/setup.md).

## The OpenAPI snapshot

`src/test/resources/openapi/api-docs.json` is a committed, normalised copy of the whole generated
document, checked by `OpenApiSnapshotTest`. Its diff in a pull request **is** the API diff a
reviewer needs — so any commit that changes the API must update it in the same commit, and a commit
that was not meant to change the API will fail the test.

When it fails, the actual document is written to `build/openapi/api-docs.actual.json`. Review the
difference first; if the change is intended, accept it with:

```bash
cp build/openapi/api-docs.actual.json src/test/resources/openapi/api-docs.json
```

There is deliberately **no** flag or task that rewrites the snapshot in place: CI could run it, and
an API change would then land unreviewed.

## Git Workflow

- Main branch: `main`
- Feature branches: `feature/conluz-XXX` (where XXX is the issue number)
- Commit format: `[conluz-XXX] Your commit message`
- Merge strategy: Squash and merge to main
- Direct pushes to `main` are not allowed

## Code Standards

- Code and comments must be in English
- **URL path parameter naming**: All REST API URL path segments that identify a resource MUST use the
  `{resourceId}` convention (e.g. `{supplyId}`, `{userId}`, `{plantId}`, `{communityId}`,
  `{sharingAgreementId}`). The bare `{id}` pattern is never used. The same name MUST be used
  consistently for the `@PathVariable` annotation value and the Java method parameter name. This
  ensures OpenAPI specs are self-documenting and eliminates ambiguity when multiple IDs appear in
  the same URL.
- All new code must have automated tests
- Architecture tests are enforced via ArchUnit (see `src/test/java/org/lucoenergia/conluz/architecture/`)
- When injecting beans, always use the interface. This also applies to integration tests
- When creating tests over services that has an interface, always use the name of the interface + "Test" for naming them
- **Never use `findAll().stream().findFirst()` in production code** to retrieve a single entity. This loads all rows into memory. Use a Spring Data derived query method that produces a `LIMIT 1` query instead — e.g., `findFirstBy()` or `findFirstByOrderByIdAsc()` in the JPA repository interface. This anti-pattern is only acceptable in test code where it avoids adding repository methods purely for test purposes.
- **JPA repositories and entities are internal infrastructure details and must never leak across layers.** Spring Data JPA repository interfaces (extending `JpaRepository`) and JPA entity classes (annotated with `@Entity`) may only be referenced within `infrastructure/` package tree. Repository implementation classes in `infrastructure/` are the sole layer where JPA/ORM types reside. Services and controllers must never receive or return JPA entities — entity mappers must convert between JPA entities and domain objects before crossing layer boundaries. Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/JpaUsageArchTest.java`) enforce this via ArchUnit.
- **All `RepositoryDatabase` classes must be annotated with `@Transactional`.** Both read and write operations should declare intent explicitly: use `@Transactional(readOnly = true)` for read-only queries and `@Transactional` for write operations. This ensures consistent transaction boundaries across all database access. Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/RepositoryTransactionalArchTest.java`) enforce this via ArchUnit.
- **`RepositoryDatabase` and `@Service` classes must not mix read-only and write transactional methods.** A class's transactional mode is declared once, at the class level: `@Transactional(readOnly = true)` if every method is read-only, or plain `@Transactional` if it contains writes. A method-level `@Transactional` is only allowed when it configures something other than `readOnly` (e.g. `propagation`, `isolation`, `timeout`, `rollbackFor`) — an override whose only effect is toggling `readOnly` away from the class default (including a bare `@Transactional` used to flip back to writable) is not allowed. A class that genuinely needs both modes must be split into separate classes by responsibility, following the existing `Get*`/`Create*`/`Update*`/`Delete*`/`Enable*`/`Disable*` naming convention (e.g. `GetSupplyRepositoryDatabase` vs `CreateSupplyRepositoryDatabase`), instead of mixing modes in one class. Architecture tests (see `RepositoryTransactionalArchTest.java` and `ServiceTransactionalArchTest.java`) enforce this via ArchUnit.
- **All authorization logic must live in the controller layer.** Access decisions are expressed via `@PreAuthorize` on controllers (delegating to the `@communityAccessGuard` bean); services and repositories must contain no access-control logic and must never call `CommunityAccessGuard` or throw `AccessDeniedException`. The only non-controller class allowed to reference `AccessDeniedException` is `ConluzAccessDeniedHandler` (the component that maps it to a 403 response). Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/AuthorizationLocationArchTest.java`) enforce this via ArchUnit.
- **Access rules live in pure policies; guards are adapters over them.** The rules themselves are written once in `domain/admin/community/access/policy`, as classes that take the caller plus an already-resolved target (`null` meaning "the lookup found nothing") and return an `AccessDecision` — `NOT_VISIBLE`, `FORBIDDEN` or `ALLOWED`. A policy has no Spring annotations, no repository, no `AuthService`, and throws nothing; `CallerMemberships` is the single spelling of the caller's standing ("enabled community admin of X", "can see community X"). The `*AccessGuardImpl` classes in `infrastructure/admin/community/access` resolve the caller, load the target, call the policy, and translate: `NOT_VISIBLE` → the aggregate's `*NotFoundException` (404), `FORBIDDEN` → `false` (403), absent caller → `false` (401). **No access rule may be written outside `..access.policy..`** — the split exists so the same rule can be evaluated both in front of one request and over a page of already-loaded entities, where a repository call per item would be an N+1 and an exception would be the wrong answer shape. Enforced by `AccessPolicyPurityArchTest`.
- **Every `@PreAuthorize` is `isAuthenticated()` or exactly one `@communityAccessGuard.<method>(...)` call.** Composite expressions (`and`, `or`, `!`) and `hasRole(...)` are not permitted — platform-wide actions use the `PlatformAccessGuard` methods (`canCreateCommunity`, `canUpdateCommunity`, …), and self-service exclusions are absorbed into the guard method (`canDeleteUser`, `canEnableUser`, `canDisableUser`). An endpoint's decision must have a single name so it can be reported to a client as a capability. Every handler method must carry one, apart from the `permitAll()` endpoints. Enforced by `PreAuthorizeShapeArchTest` and `PreAuthorizePresenceArchTest`.
- **Controllers must be thin: no domain logic and no calls to the repository layer.** A controller may only (1) enforce authorization via `@PreAuthorize`, (2) bind and validate the request (path variables, `@Valid @RequestBody`), (3) delegate to a single domain service call, and (4) map the service result to the HTTP response. All business logic — conditional dispatch/branching, precondition checks (e.g. "is Datadis enabled for this community?"), iteration, orchestration, and any repository access — must live in a service (`domain/**` interface with its `infrastructure/**` `*Impl`). Controllers must never inject or call a repository (`*Repository`, `RepositoryDatabase`, `*RepositoryInflux`) directly; they depend only on service interfaces. If a controller needs data or a guard condition, add a service method for it rather than reaching into a repository or embedding an `if` that encodes a business rule. The repository-access half of this rule is enforced via ArchUnit (see `src/test/java/org/lucoenergia/conluz/architecture/ControllerRepositoryAccessArchTest.java`).
- **Response schema annotations: every `*Response` field must declare required-ness, and nullable fields use `types`, never `nullable`.** Jackson's default null-inclusion in this app is `ALWAYS` (verified empirically — no `spring.jackson.default-property-inclusion`, no `@JsonInclude`, no custom `ObjectMapper` bean), so every response field's JSON key is always present; declare every field in the class-level `@Schema(requiredProperties = {...})`, mirroring the convention `*Body` request DTOs already use. If a field's *value* can be `null`, additionally annotate it `@Schema(types = {"<T>", "null"})` (e.g. `types = {"string", "null"}`) — restate the base type explicitly, never `types = {"null"}` alone. **Never use `@Schema(nullable = true)`**: springdoc/swagger-core generates OpenAPI 3.1, under which `nullable` is silently dropped (verified — it never appears anywhere in the generated document, even on fields that carry it) with no compiler or runtime warning. A response DTO nesting a nullable `$ref` object additionally needs the `conluz-web` Orval `input.override.transformer` (rewrites `{$ref, type:[...,"null"]}` into `{anyOf:[{$ref},{type:"null"}]}`) to type correctly client-side — Orval otherwise silently drops nullability when a `$ref` sibling is present. Also never write a `@Schema(example = "...")` string that starts with a bare number/boolean/null token followed by whitespace or end-of-string (e.g. `"2024 winter distribution"`) — springdoc's Jackson-based example resolution renders it as that scalar, not the intended string (e.g. the number `2024`). Both conventions are enforced via ArchUnit (see `ResponseSchemaNullabilityArchTest.java` and `SchemaExampleArchTest.java`).
- **Entity references in responses are nested objects, not flat ids.** A reference from one entity to another (e.g. a plant's owning community) is serialised in responses as a nested object carrying at least an `id`, never as a flat `<entity>Id` scalar, so fields can be added to it additively later instead of requiring an incompatible flat-to-nested restructure. This applies to entity references only (not scalar attributes) and to responses only (request bodies keep flat identifiers); existing flat references are not retrofitted, since that retrofit would itself be the incompatible restructure this convention exists to avoid.

- **A new endpoint needs a guard, an inventory entry and — if it acts on a resource — a capability.**
  The guard is one `@PreAuthorize` delegating to `@communityAccessGuard`; the entry maps that guard
  to the capability reporting it in `CapabilityInventory` (test code), or records why nothing
  reports it; the capability is a required boolean on the resource's `*CapabilitiesResponse`,
  assembled in `infrastructure/admin/community/access/capability` from the same policy the guard
  uses. A capability expresses **authorization only** — never the resource's state — and a listing
  must never assemble one with a query per item. `CapabilityCoverageArchTest` fails the build while
  a guard is unclassified, and `CapabilityGuardEquivalenceTest` fails if a capability and its guard
  disagree. Full reference and a checklist: [`docs/security/capability-inventory.md`](docs/security/capability-inventory.md).

## Security & Authorization Policy

This policy is MANDATORY. Every REST controller endpoint MUST enforce it via a `@PreAuthorize` clause (delegating to the `@communityAccessGuard` bean when community/object scope is required). **All authorization lives in the controller layer** — services and repositories must contain no access-control logic. See the full policy (roles, role privileges, enforcement rules, 401/403/404 error mapping, and 409 state conflicts) in [`docs/security/authorization-policy.md`](docs/security/authorization-policy.md).

Login and password change are throttled per account and per client address against password guessing: failed attempts are counted in memory, a slot is reserved before any password is checked, and an attempt over the limit is answered 429 with `Retry-After`. Any change to those endpoints, to how a password is verified, or to how the client address is resolved (`CONLUZ_TRUSTED_PROXIES`) must keep that behaviour. How it works, with flow diagrams: [`docs/security/authentication-throttling.md`](docs/security/authentication-throttling.md).

## GitHub CLI

`gh` is authenticated with a **read-only** credential and is available for reading. Use it whenever
it saves a guess: checking an issue number before referencing it, reading a pull request's review
comments, looking at why a workflow run failed, listing releases, labels or tags.

**Never perform a write.** That covers creating, editing, closing, commenting on, reviewing or
merging issues and pull requests; labels, releases and milestones; running, re-running or cancelling
workflows; changing repository or organisation settings; and any `gh api` call with a method other
than GET, GraphQL mutations included. `gh auth login`, `gh auth refresh`, `gh alias set` and
`gh extension install` are equally off limits — they are ways to change what the tool can do.

Writes fail because the credential has no write permission. A contributor may also block the
commands with `permissions.deny` rules in their own untracked `.claude/settings.local.json`; the
repository commits none. Do not work around either. If a command is refused, report it; do not look
for a spelling that gets through, and never propose changing the deny rules or the credential.

When a task appears to need a write — "open an issue for this", "comment on that PR", "merge it" —
produce the content and say exactly where it goes (repository, issue or PR number, and the label or
milestone if relevant), so a human can post it in one paste. Do not treat the restriction as a
blocker to report and stop at: the deliverable is the text, not the API call.

`git push` is likewise not yours to run. Commit locally, and leave pushing and opening pull requests
to a human.

### Referring to issues in code

When a comment, a suppression justification, a `TODO` or a test name refers to work, use an issue
number (`#412`) or its URL — never an epic's internal ordering ("epic PR 5"), a branch name, a
milestone or a date. Branches are deleted after merge and plans are not in the repository; an issue
number resolves years later from a fresh clone.

`gh issue list` and `gh issue view` are there precisely so the number can be checked rather than
invented. If the issue does not exist yet, ask for it: a temporary exemption with no issue behind it
is a permanent one.

### Never create a branch

**Do not run `git checkout -b`, `git branch`, `git switch -c` or `git worktree add`.** Branches are
created by a human, usually from the right remote base and often before the work is handed over.

Work on the branch that is already checked out. If the task needs a branch that is not there:
**stop and ask for it by name**, saying which base it should come from. Do not create it "to
unblock yourself" — that is the slowest option available, not the fastest.

The same staleness rule applies to reading git facts at all: establish them from `git fetch` plus
`git ls-remote` or `origin/<branch>`, never from a local branch ref that may not have moved in
weeks.

## PR description
Once every work finishes on a branch, generate a PR description in english and markdown format ready to be pasted in GitHub. Generate it in a file on /tmp folder and give me the full path to the file.
