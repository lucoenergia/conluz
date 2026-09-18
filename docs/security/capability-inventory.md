# Capability inventory

Every resource response carries a `capabilities` object telling the caller what they may do with
that resource. The values are computed by the **same policies** that authorize the endpoints
(`domain/admin/community/access/policy`), so a client can render its whole UI without re-deriving a
single access rule.

This document is the reference for what is reported and why. The machine-checkable copy is
`src/test/java/org/lucoenergia/conluz/architecture/CapabilityInventory.java`; if the two ever
disagree, that file is right, because the build enforces it.

See also [`authorization-policy.md`](authorization-policy.md) for the rules themselves, and for the
401/403/404 mapping a capability collapses.

## The rules

1. **Authorization only, never state.** A capability answers "may this caller do this?" and nothing
   else. An agreement that is already published still reports `canManage = true` to an admin: the
   admin *is* allowed to manage it, and it is the agreement's `status` that says the action would be
   rejected right now. Clients read both. Folding state in would make the field mean two things and
   leave no way to tell which one denied it.
2. **The same policies as the guards.** An assembler calls the policy a guard calls, and maps
   `ALLOWED` to `true` and anything else to `false`. It holds no rules of its own. `AccessPolicies`
   hands both sides one instance, so they cannot drift.
   `CapabilityGuardEquivalenceTest` computes every capability both ways over a matrix of callers and
   fails if they disagree.
3. **No per-item queries.** A listing must cost the same number of statements whatever its page
   holds. Most capabilities are decided from the caller's memberships and the entity already loaded,
   so they cost nothing; where a rule needs more (the target's memberships, or an agreement's
   plant), the controller loads it **once for the page**. `ListEndpointQueryCountTest` compares one
   item against five on all seven listings.
4. **References carry no capabilities.** The `*ReferenceResponse` types in `shared/web/reference` are
   identifiers, not resources. When a response embeds a reference the client may navigate to, the
   permission to open it is a capability of the **embedding** resource, named `canRead<Reference>`.
   `PlantResponse.supply` is the one case implemented today: it is a `SupplyReferenceResponse`
   carrying no owner, and the plant reports `canReadSupply`.

Nested value objects (contract, distributor, Shelly, files, coefficients) carry no capabilities
either, and neither do the responses of `permitAll` or plain `isAuthenticated()` endpoints —
including `PUT /users/profile`, which any authenticated caller may use on themselves.

## What is reported

### Platform — `PlatformCapabilitiesResponse`

Returned only by `GET /api/v1/users/current`. These are facts about the caller, not about a user, so
they are deliberately **not** on `UserResponse`: that type is frequently somebody else, and a client
must not read another person's record and conclude something about its own permissions.

| Capability | Guard | Endpoint |
|---|---|---|
| `canCreateCommunity` | `canCreateCommunity` | `POST /communities` |
| `canListUsers` | `canListUsers` | `GET /users` |

### Community — `CommunityResponse.capabilities`

| Capability | Guard |
|---|---|
| `canRead` | `canReadCommunity` |
| `canUpdate` | `canUpdateCommunity` |
| `canEnable` | `canEnableCommunity` |
| `canDisable` | `canDisableCommunity` |
| `canManage` | `canManageCommunity` |
| `canManageMemberships` | `canManageMemberships` |
| `canManageMembershipInvestment` | `canManageMembershipInvestment` |
| `canListPlants` | `canListPlants` |
| `canCreatePlants` | *(no guard — see below)* |
| `canCreateUsers` | `canCreateUserIn` |
| `canReadProduction` | `canReadCommunityProduction` |
| `canListSupplies` | `canListSupplies` |

`canUpdate`, `canEnable` and `canDisable` are platform-wide decisions: administering a community does
not confer them. `canReadProduction` and `canListSupplies` are the opposite — membership, not
administration, so a platform admin who is not a member gets neither.

### Supply — `SupplyResponse.capabilities`

| Capability | Guard |
|---|---|
| `canRead` | `canReadSupply` |
| `canEdit` | `canEditSupply` |
| `canReadPartitionCoefficients` | `canReadSupplyPartitionCoefficients` |
| `canCreatePlant` | `canCreatePlant(supplyCode)` |

The owner's shape is the one no role name conveys: they may read their supply and the coefficients
describing their own share, but may not edit it or hang a plant off it.

### Plant — `PlantResponse.capabilities`

| Capability | Guard |
|---|---|
| `canRead` | `canReadPlant` |
| `canManage` | `canManagePlant` |
| `canListSharingAgreements` | `canListSharingAgreements` |
| `canManageSharingAgreements` | `canManageSharingAgreement(plantId)` |
| `canReadSupply` | `canReadSupply(plant.supply.id)` |

`canReadSupply` is rule 4 above. Listing plants is open to any member; the supply behind one is not.

### Sharing agreement — `SharingAgreementResponse.capabilities`

| Capability | Guard |
|---|---|
| `canRead` | `canReadSharingAgreement` |
| `canManage` | `canManageSharingAgreement(plantId, agreementId)` |

Both are the same rule today, and every endpoint returning an agreement already demands it — so no
caller can observe a `false`: one who would have was refused the response. They are reported anyway
because clients read capabilities uniformly, and the two rules are kept apart so they can diverge
without a call site changing.

### User — `UserResponse.capabilities`

| Capability | Guard |
|---|---|
| `canRead` | `canReadUser` |
| `canEdit` | `canEditUser` |
| `canDelete` | `canDeleteUser` |
| `canEnable` | `canEnableUser` |
| `canDisable` | `canDisableUser` |
| `canGrantPlatformAdmin` | `canGrantPlatformAdmin` |
| `canRevokePlatformAdmin` | `canRevokePlatformAdmin` |
| `canListSupplies` | `canListSuppliesOfUser` |

`canEdit` is `false` for an ordinary member reading their own record, on purpose: name, DNI and
member number are an administrative change, and contact details go through `PUT /users/profile`.
`canDelete`, `canEnable`, `canDisable` and `canRevokePlatformAdmin` are always `false` when the user
is the caller — nobody may act on themselves that way, admins included.

### Membership — `MembershipResponse.capabilities`

| Capability | Guard |
|---|---|
| `canUpdateRole` | `canManageMemberships(communityId)` |
| `canDelete` | `canManageMemberships(communityId)` |
| `canManageInvestment` | `canManageMembershipInvestment(communityId)` |
| `canReadPayback` | `canReadMembershipPayback(communityId, userId)` |

A platform admin may administer a roster without being able to touch its members' money: the
investment and payback rules have no platform-admin branch, and `canManageMemberships` does.

## What is not reported

### Guards reported to nobody

| Guard | Why |
|---|---|
| `isCommunityAdminOfSupply` | Shapes a response body rather than gating a request — the partition-coefficient history passes it as `includePending`. A client that knew it would learn nothing the response does not already show. |
| `isMemberOfCommunity` | The rule behind `canReadCommunityProduction` and `canListSupplies`, which are what the endpoints use and what the community reports. Naming it again would report one decision twice. |
| `visibleCommunityIds` | A scope for a query, not a decision about an object. The listing it scopes reports its own capabilities on each item. |
| `adminCommunityIds` | As above. |
| `isCurrentUser` | A fact the client already has. The rules that care about it fold it in and are reported themselves. |

### Capabilities with no guard

`community.canCreatePlants` has none: creating a plant always names a supply, so no endpoint asks
the question community-wide. The community reports it anyway, because a client has to decide whether
to offer the action before any supply is chosen. It is backed by `PlantAccessPolicy.canCreateIn`,
which is the community-admin half of `canCreate`, and a policy test pins the two together.

A `true` here is necessary but not sufficient for any particular supply —
`SupplyCapabilitiesResponse.canCreatePlant` answers that.

## How to add a capability

1. **Put the rule in a policy.** `..admin.community.access.policy..`, returning an `AccessDecision`.
   Never in a guard, never in an assembler — `AccessPolicyPurityArchTest` and the split exist so the
   same rule can be evaluated in front of one request and over a page.
2. **Give the endpoint a guard method** that adapts the policy, and reference it from a single
   `@PreAuthorize` (`PreAuthorizeShapeArchTest` allows exactly one call).
3. **Add the field** to the resource's `*CapabilitiesResponse`, as a `boolean` listed in the
   class-level `@Schema(requiredProperties = {...})` with a `description`. Use the builder.
4. **Assemble it** in the resource's assembler, from the policy, mapping `ALLOWED` to `true`. If the
   rule needs data the entity does not carry, load it **once per page** in the controller — never per
   item.
5. **Record it** in `CapabilityInventory`: map the guard to `(resource, field)`, or mark the guard
   `SERVER_ONLY` with the reason, or add the capability to `withoutGuard()`. The build fails until
   you do.
6. **Extend the equivalence test** so the new field is computed both ways, and the **HTTP test** for
   that resource so a client's view of it is asserted.
7. **Refresh the OpenAPI snapshot** (`cp build/openapi/api-docs.actual.json
   src/test/resources/openapi/api-docs.json`) in the same commit, and note the change for
   `conluz-web` — a new required field is a breaking change for a generated client.
8. **Update this document.**

## Where the tests are

| Test | What it defends |
|---|---|
| `CapabilityCoverageArchTest` | Every guard is classified, every field is backed by one, nothing stale in either direction. |
| `CapabilityGuardEquivalenceTest` | Every capability equals its guard's outcome, over a matrix of callers. |
| `ListEndpointQueryCountTest` | No listing issues a query per item. |
| `*CapabilitiesAssemblerTest` | The rules themselves, per assembler. |
| `*CapabilitiesEndpointTest` | What a client actually receives, per resource. |
| `OpenApiSnapshotTest` | The published contract, so a schema change is visible in review. |
