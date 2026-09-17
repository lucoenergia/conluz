# Security & Authorization Policy

This policy is MANDATORY. Every REST controller endpoint MUST enforce it via a `@PreAuthorize` clause (delegating to the `@communityAccessGuard` bean when community/object scope is required). **All authorization lives in the controller layer** — services and repositories must contain no access-control logic (no `CommunityAccessGuard` calls, no `AccessDeniedException`); this is enforced by `AuthorizationLocationArchTest`. Within that layer the rules themselves live in **pure policies** (`domain/admin/community/access/policy`), and the guards are adapters over them — see "Policies and guards" below. The `@Operation` description MUST state the required role(s) so Swagger matches the guard. No endpoint may rely on being "internal" — every endpoint is authorized.

## Roles
- **Platform admin** — `User.isPlatformAdmin() == true` → authority `ROLE_PLATFORM_ADMIN`.
- **Community admin** — enabled membership with `CommunityRole.COMMUNITY_ADMIN`.
- **Member / regular user** — enabled membership without admin role.

## Capabilities
- Platform admins can:
  - List, view, create, edit and remove users globally.
  - List, view, create, edit and remove communities.
  - Add and remove admins to/from communities.
- Community admins (scoped to the community they administer) can:
  - Import, create, edit, view, list and remove members.
  - Import, create, edit, view, list and remove supplies of those members.
  - Create, edit, view, list and remove plants.
  - Create, edit, view, list and remove sharing agreements.
  - Manage supply/plant config (Huawei, Datadis, Shelly).
  - Get consumption and production data of any supply or plant they administer.
- Regular users (non-admins) can:
  - See data about supplies they own.
  - See production data of their community/communities.
- Any authenticated user can get prices.
- Any user can modify their **own contact details** — email, address and phone number — through
  `PUT /api/v1/users/profile`. Name, DNI (`personalId`) and member number are an administrative
  change: they go through `PUT /api/v1/users/{userId}`, which is Platform Admin or Community Admin
  only. Nobody may enable, disable or delete themselves.

### What a platform admin does **not** get

Being a platform admin is scoped to the three bullets above — users, communities, and community-admin
membership. It confers **no access to a supply, plant or sharing agreement**: on those, a platform
admin who neither administers the owning community nor owns the supply is answered exactly like any
other stranger, with a **404**. This is deliberate (`c8bd9554`, "Reviewed and fixed some
missconceptions of the privilege surface of platform admin users"), and every affected endpoint's
`@Operation` description says so — e.g. `GET /supplies/{supplyId}` reads *"Required: Community Admin
of the supply's community, or the supply owner."* Do not "restore" the bypass as if it were an
oversight.

The consequence is an asymmetry that is intended, not a bug: on **community-scoped** endpoints a
non-member platform admin gets **403** (`isMemberOfCommunity`, `canReadCommunityProduction`,
`canListSupplies`, `canListPlants`), because they *can* see the community and the denial leaks
nothing; on a **single object** they get **404** (`canReadSupply`, `canReadPlant`,
`canReadSharingAgreement`), because they cannot see that object and a 403 would confirm it exists.

## Enforcement rules for developers and AI agents
- **Every `@PreAuthorize` is either `isAuthenticated()` or exactly one `@communityAccessGuard.<method>(...)` call.** Composite expressions (`and`, `or`, `!`) and `hasRole(...)` are not permitted: an endpoint's decision must have a single name, so it can be reported to a client as a capability and evaluated outside a request. Enforced by `PreAuthorizeShapeArchTest`; `PreAuthorizePresenceArchTest` additionally requires every handler method to carry one (the `permitAll()` endpoints are an explicit allowlist).
- Platform-wide actions: use the platform guard methods — `canCreateCommunity()`, `canUpdateCommunity(#communityId)`, `canEnableCommunity(#communityId)`, `canDisableCommunity(#communityId)`, `canGrantPlatformAdmin(#userId)`, `canRevokePlatformAdmin(#userId)` — never `hasRole('PLATFORM_ADMIN')` in SpEL. These are role checks: they take the object id only so the decision has a subject to be named against, they never inspect it, and they never throw. A denial is always **403**, never 404, exactly as `hasRole` behaved.
- Community-scoped actions: `@PreAuthorize("@communityAccessGuard.<method>(...)")` using the matching guard method (`canManageCommunity`, `canManageMemberships`, `canManagePlant`, `canCreatePlant`, `canManageSharingAgreement`, `canEditSupply`, `canCreateUserIn`, `canReadUser`, `canEditUser`, `canListUsers`).
- Sharing agreements are admin-only for both reads and writes: use `canReadSharingAgreement` (single-agreement reads), `canListSharingAgreements(#plantId)` (the list endpoint) or `canManageSharingAgreement(#plantId)` (creation) — never `canReadPlant` — to guard sharing-agreement endpoints, so a future endpoint does not silently reopen member-level read access.
- Partition-coefficient reads scoped to a single supply use `canReadSupplyPartitionCoefficients(#supplyId)`, never `canReadSupply`. It is the same decision as `canEditSupply` today and is kept apart on purpose, so a read guarded as a write is nameable and the two can diverge without touching call sites. (`GetPartitionCoefficientHistoryController` is still on `canReadSupply`; whether it should align is an open question.)
- Object reads scoped to ownership/community: enforce `canReadSupply` / `canReadCommunity` (or the matching object-scoped guard method) directly in the controller `@PreAuthorize` (never in the service). The guard method itself throws the matching `*NotFoundException` (→ 404) when the caller cannot see the object — controllers MUST NOT add their own `if (!guard.canX(id)) throw ...` / `ResponseEntity.notFound()` boilerplate. See "Error responses for denied access" below.
- List endpoints: compute the visible scope in the controller via the guard (`visibleCommunityIds()` for membership scope, `adminCommunityIds()` for admin-only scope) and pass it as a plain parameter to the service/repository query — the service must not call the guard itself.
- `isAuthenticated()` alone is acceptable ONLY for endpoints any authenticated user may call without object scope (e.g. `GET /prices`). Otherwise use a `@communityAccessGuard` method.
- Self-service: enabling, disabling or deleting one's own account MUST be rejected for everyone, including admins. That rule lives inside `canDeleteUser`, `canEnableUser` and `canDisableUser` rather than in SpEL; each settles the edit decision first and only then refuses a caller acting on themselves, so a caller who cannot see the target still gets a 404 and a caller who can gets a 403.
- Self-service edits go through their **own endpoint**, not through a widened guard: `PUT /api/v1/users/profile` is `isAuthenticated()` and acts on the caller, so it needs no object-scoped rule and cannot be pointed at anyone else. `canEditUser` therefore stays an administrative decision — a plain member editing themselves via `PUT /users/{userId}` is a **403**, on purpose. Add a self-service endpoint rather than a self branch when a user needs to change their own data.
- New endpoints without an authorization clause are NOT permitted. Add a controller test for every endpoint asserting **401** (no token); **404** when an authenticated caller cannot see the targeted object (object-scoped denial); and **403** when the caller can see the object but lacks permission for the action (role/scope denials and self-service).

## Error responses for denied access (401 / 403 / 404)

To avoid leaking the existence of resources, denials are mapped by **visibility**, not just by role:
- **401 Unauthorized** — the request is unauthenticated.
- **404 Not Found** — the authenticated caller **cannot see** the targeted object (it does not exist, or it is outside everything they may read). Returning 403 here would reveal that the object exists.
- **403 Forbidden** — the authenticated caller **can see** the object but is **not permitted to perform this action** (e.g. a non-admin community member hitting a community-admin-only endpoint). They already know it exists, so nothing leaks. Also used for platform-wide role checks (the `PlatformAccessGuard` methods, which never answer 404) and self-service guards (enabling/disabling/deleting one's own account).

How this is enforced (centralized in the guard — no controller boilerplate):
- Object-scoped `@communityAccessGuard` methods perform a **visibility gate then an authorization check**: they **throw the matching `*NotFoundException`** (`CommunityNotFoundException`, `SupplyNotFoundException`, `PlantNotFoundException`, `UserNotFoundException`, `SharingAgreementNotFoundException`) when the caller cannot see the object, and otherwise **return** whether the action is allowed (`false` → 403). They return `false` (never throw) only when the caller is unauthenticated, so anonymous requests become 401.
- Controllers reference the guard directly in `@PreAuthorize` (e.g. `@PreAuthorize("@communityAccessGuard.canReadSupply(#id)")`); they add **no** not-found/forbidden boilerplate. A `*NotFoundException` thrown during `@PreAuthorize` evaluation is mapped to 404 by the global `@RestControllerAdvice` handlers; a `false` result is mapped to 403 by `ConluzAccessDeniedHandler` (or 401 for anonymous callers).
- This is why an object-scoped denial must NEVER be left to fall through to a 403 when the caller cannot see the object — the guard decides 404 vs 403. Throwing a domain `*NotFoundException` from the guard does not violate the "only `ConluzAccessDeniedHandler` references `AccessDeniedException`" rule (it is a different exception type).

## Policies and guards

The rules are written once, in **pure policies** under `domain/admin/community/access/policy`, and
applied by **guard adapters** under `infrastructure/admin/community/access`.

- A policy takes the caller and an **already-resolved** target (or `null`, meaning the lookup found
  nothing) and returns an `AccessDecision`: `NOT_VISIBLE`, `FORBIDDEN` or `ALLOWED`. It has no Spring
  annotations, no repository, no `AuthService`, and throws nothing. `AccessPolicyPurityArchTest`
  enforces this.
- A guard adapter resolves the caller and loads the target, calls the policy, and translates:
  `NOT_VISIBLE` → throw the aggregate's `*NotFoundException` (→ 404), `FORBIDDEN` → `false` (→ 403),
  `ALLOWED` → `true`. An absent caller is `false` before any policy runs (→ 401).
- `CallerMemberships` is the single spelling of the caller's standing — "enabled community admin of
  X", "can see community X", "is this user". Add membership questions there, not in a guard.

### Null arguments reach a guard only from an unvalidated source

Argument resolution and `@Valid` run **before** `@PreAuthorize`: `InvocableHandlerMethod` resolves
every argument — deserialising and validating the body, converting `String` to `UUID` — and only then
invokes the proxied controller method where method security evaluates the SpEL. A malformed path
variable is therefore a 400, never a null, and a body field carrying `@NotNull`/`@NotBlank` is a 400
before any guard runs (`CreateSupplyControllerTest` omits the required `communityId` and asserts 400,
where a guard-first order would have produced 404).

So a guard can only see a null from a **request-body field with no validation annotation** or a
`@RequestParam(required = false)`. Today that is `CreateUserBody.communityId` (deliberate — a platform
admin may create a user attached to no community) and `CreateSuppliesWithFileController`'s optional
`communityId`. Every other null-argument branch in the guards is defence in depth and is not
reachable over HTTP; do not treat those branches as live behaviour, and do not add null guards for
path variables.

**No access rule may be written outside `..admin.community.access.policy..`.** The split exists so
the same rule can be evaluated two ways: in front of one request, where a denial must become a
status code, and over a page of already-loaded entities, where loading per item would be an N+1 and
an exception would be the wrong answer shape. A rule spelled in a guard would be available only to
the first.

## State conflicts (409)

`409 Conflict` is **not** an authorization outcome — it signals that a request which is authenticated, authorized and well-formed cannot be applied because it **conflicts with the current state of the resource** (a precondition/invariant violation, not a missing permission or a malformed body). Use it — never 400, 403 or 422 — whenever the operation is legal for this caller but the target's state forbids it right now.

Established uses in this codebase:
- **Integration disabled** — hitting a manual sync / config-dependent endpoint while the integration is turned off: `DatadisDisabledException`, `ShellyDisabledException`, `HuaweiDisabledException` (e.g. auto-sync is enabled, so the Huawei manual sync endpoints respond `409`).
- **Invariant would be broken** — an action that would violate a domain invariant, e.g. revoking the last platform admin (`LastPlatformAdminException`).

Enforcement rules for developers and AI agents:
- Model each conflict as a dedicated domain exception thrown by the **service** (not the controller), and map it to `HttpStatus.CONFLICT` in the module's `@RestControllerAdvice` `*ExceptionHandler` via `errorBuilder.build(message, HttpStatus.CONFLICT)`, with an i18n message key. Do not build the `ResponseEntity` in the controller.
- Document the `409` response on the endpoint's `@Operation`/`@ApiResponse` so Swagger matches the behavior.
- Add a controller test asserting **409** for the conflicting-state case (alongside the 401/403/404 tests required above).
