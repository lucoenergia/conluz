# Security & Authorization Policy

This policy is MANDATORY. Every REST controller endpoint MUST enforce it via a `@PreAuthorize` clause (delegating to the `@communityAccessGuard` bean when community/object scope is required). **All authorization lives in the controller layer** — services and repositories must contain no access-control logic (no `CommunityAccessGuard` calls, no `AccessDeniedException`); this is enforced by `AuthorizationLocationArchTest`. The `@Operation` description MUST state the required role(s) so Swagger matches the guard. No endpoint may rely on being "internal" — every endpoint is authorized.

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
- Any user can modify their own data, but CANNOT enable/disable or delete themselves.

## Enforcement rules for developers and AI agents
- Platform-wide actions: `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`.
- Community-scoped actions: `@PreAuthorize("@communityAccessGuard.<method>(...)")` using the matching guard method (`canManageCommunity`, `canManageMemberships`, `canManagePlant`, `canCreatePlant`, `canManageSharingAgreement`, `canEditSupply`, `canCreateUserIn`, `canReadUser`, `canEditUser`, `canListUsers`).
- Object reads scoped to ownership/community: enforce `canReadSupply` / `canReadCommunity` (or the matching object-scoped guard method) directly in the controller `@PreAuthorize` (never in the service). The guard method itself throws the matching `*NotFoundException` (→ 404) when the caller cannot see the object — controllers MUST NOT add their own `if (!guard.canX(id)) throw ...` / `ResponseEntity.notFound()` boilerplate. See "Error responses for denied access" below.
- List endpoints: compute the visible scope in the controller via the guard (`visibleCommunityIds()` for membership scope, `adminCommunityIds()` for admin-only scope) and pass it as a plain parameter to the service/repository query — the service must not call the guard itself.
- `isAuthenticated()` alone is acceptable ONLY for endpoints any authenticated user may call without object scope (e.g. `GET /prices`). Otherwise use a `@communityAccessGuard` method.
- Self-service: a user editing their own record is allowed; enabling/disabling/deleting one's own account MUST be rejected for everyone, including admins (e.g. `@PreAuthorize("@communityAccessGuard.canEditUser(#userId) and !@communityAccessGuard.isCurrentUser(#userId)")`).
- New endpoints without an authorization clause are NOT permitted. Add a controller test for every endpoint asserting **401** (no token); **404** when an authenticated caller cannot see the targeted object (object-scoped denial); and **403** when the caller can see the object but lacks permission for the action (role/scope denials and self-service).

## Error responses for denied access (401 / 403 / 404)

To avoid leaking the existence of resources, denials are mapped by **visibility**, not just by role:
- **401 Unauthorized** — the request is unauthenticated.
- **404 Not Found** — the authenticated caller **cannot see** the targeted object (it does not exist, or it is outside everything they may read). Returning 403 here would reveal that the object exists.
- **403 Forbidden** — the authenticated caller **can see** the object but is **not permitted to perform this action** (e.g. a non-admin community member hitting a community-admin-only endpoint). They already know it exists, so nothing leaks. Also used for platform-wide role checks (`hasRole('PLATFORM_ADMIN')`) and self-service guards (enabling/disabling/deleting one's own account).

How this is enforced (centralized in the guard — no controller boilerplate):
- Object-scoped `@communityAccessGuard` methods perform a **visibility gate then an authorization check**: they **throw the matching `*NotFoundException`** (`CommunityNotFoundException`, `SupplyNotFoundException`, `PlantNotFoundException`, `UserNotFoundException`, `SharingAgreementNotFoundException`) when the caller cannot see the object, and otherwise **return** whether the action is allowed (`false` → 403). They return `false` (never throw) only when the caller is unauthenticated, so anonymous requests become 401.
- Controllers reference the guard directly in `@PreAuthorize` (e.g. `@PreAuthorize("@communityAccessGuard.canReadSupply(#id)")`); they add **no** not-found/forbidden boilerplate. A `*NotFoundException` thrown during `@PreAuthorize` evaluation is mapped to 404 by the global `@RestControllerAdvice` handlers; a `false` result is mapped to 403 by `ConluzAccessDeniedHandler` (or 401 for anonymous callers).
- This is why an object-scoped denial must NEVER be left to fall through to a 403 when the caller cannot see the object — the guard decides 404 vs 403. Throwing a domain `*NotFoundException` from the guard does not violate the "only `ConluzAccessDeniedHandler` references `AccessDeniedException`" rule (it is a different exception type).

## State conflicts (409)

`409 Conflict` is **not** an authorization outcome — it signals that a request which is authenticated, authorized and well-formed cannot be applied because it **conflicts with the current state of the resource** (a precondition/invariant violation, not a missing permission or a malformed body). Use it — never 400, 403 or 422 — whenever the operation is legal for this caller but the target's state forbids it right now.

Established uses in this codebase:
- **Integration disabled** — hitting a manual sync / config-dependent endpoint while the integration is turned off: `DatadisDisabledException`, `ShellyDisabledException`, `HuaweiDisabledException` (e.g. auto-sync is enabled, so the Huawei manual sync endpoints respond `409`).
- **Invariant would be broken** — an action that would violate a domain invariant, e.g. revoking the last platform admin (`LastPlatformAdminException`).

Enforcement rules for developers and AI agents:
- Model each conflict as a dedicated domain exception thrown by the **service** (not the controller), and map it to `HttpStatus.CONFLICT` in the module's `@RestControllerAdvice` `*ExceptionHandler` via `errorBuilder.build(message, HttpStatus.CONFLICT)`, with an i18n message key. Do not build the `ResponseEntity` in the controller.
- Document the `409` response on the endpoint's `@Operation`/`@ApiResponse` so Swagger matches the behavior.
- Add a controller test asserting **409** for the conflicting-state case (alongside the 401/403/404 tests required above).
