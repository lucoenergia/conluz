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
   so they cost nothing; where a rule needs more (the target's memberships, an agreement's plant, a
   coefficient period's plant and agreement), it is loaded **once for the page** — by the controller,
   or by an assembler that takes the whole page (`UserCapabilitiesAssembler.assembleAll…`,
   `PartitionCoefficientCapabilitiesAssembler.assembleAll`). `ListEndpointQueryCountTest` compares one
   item against five on all seven listings and on the three coefficient reads;
   `CoefficientWriteQueryCountTest` does the same for the write endpoints that return coefficients.
4. **References carry no capabilities.** The `*ReferenceResponse` types in `shared/web/reference` are
   identifiers, not resources. When a response embeds a reference the client may navigate to, the
   permission to open it is a capability of the **embedding** resource, named `canRead<Reference>`,
   and it is computed from the rule of **the endpoint that following the reference calls** — so a
   link that is shown can never be refused. The cases implemented today:

   | Embedding response | Reference | Capability | Rule of |
   |---|---|---|---|
   | `PlantResponse` | `supply` | `capabilities.canReadSupply` | `GET /supplies/{supplyId}` |
   | `PartitionCoefficientResponse` | `sharingAgreement` | `capabilities.canReadSharingAgreement` | `GET /plants/{plantId}/sharing-agreements/{sharingAgreementId}` |

   A new reference a client may follow gets a row here, a capability on its embedding resource, and
   an equivalence assertion against that endpoint's guard.

Nested value objects (contract, distributor, Shelly, files) carry no capabilities either, and
neither do the responses of `permitAll` or plain `isAuthenticated()` endpoints — including
`PUT /users/profile`, which any authenticated caller may use on themselves. A partition coefficient
period is not a resource a caller acts on either; it carries capabilities only because of rule 4.

## What is reported

### Platform — `PlatformCapabilitiesResponse`

Returned only by `GET /api/v1/users/current`. These are facts about the caller, not about a user, so
they are deliberately **not** on `UserResponse`: that type is frequently somebody else, and a client
must not read another person's record and conclude something about its own permissions.

| Capability | Guard | Endpoint |
|---|---|---|
| `canCreateCommunity` | `canCreateCommunity` | `POST /communities` |
| `canListUsers` | `canListUsers` | `GET /users` |
| `canAdministerPlatform` | *(no guard — see below)* | — |
| `canCreateUsers` | `canCreateUserIn(null)` | `POST /users` with no `communityId` |

Both of the last two sit on the **platform** scope because neither names a resource.
`canAdministerPlatform` gates a *surface* — the communities administration page, the platform
overview, the landing route. `canCreateUsers` is the no-community case of creating a user;
`CommunityResponse.capabilities.canCreateUsers` answers the same question for one community, which is
what a community admin gets.

Note what `canAdministerPlatform` is *not*. `GET /api/v1/communities` is deliberately ungated — any
authenticated caller may call it, scoped to the communities they can see — so who reaches the
communities **administration page** is not a fact about that listing. It is a decision about
administering the platform, and this is the capability that says so.

Three of the four coincide today, and the doc says so rather than leaving a reader to discover it:
`canCreateCommunity` and `canAdministerPlatform` are the *same expression*, both reading
`PlatformAccessPolicy.canAdministerPlatform` — there is no `canCreateCommunity` policy method — and
`UserAccessPolicy.canCreateIn(caller, null)` reduces to the same predicate a third time, since its
platform-admin branch runs before the community is considered. They answer different questions —
"may I open the administration surface", "may I create a community", "may I create a user belonging
to no community" — and are kept apart so any one can diverge without a call site changing, the same
argument made below for the sharing-agreement pair.

The cost of that, stated plainly: while they coincide **no test can tell them apart**, so swapping
one for another would be caught only once the rules diverge. The guard against it is a comment on
`PlatformCapabilitiesAssembler` and the `isPlatformAdmin` assertion described under "Capabilities
with no guard".

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

### Partition coefficient — `PartitionCoefficientResponse.capabilities`

| Capability | Guard |
|---|---|
| `canReadSharingAgreement` | `canReadSharingAgreement(plantId, sharingAgreementId)` |

This is rule 4: a period's `sharingAgreement` is a reference, so the period says whether following
it would succeed. It is computed from `SharingAgreementAccessPolicy.canReadThroughPlant` — the rule
of the endpoint the link opens — over the period's **plant**, not the supply's community. A plant can
be repointed at a supply of another community, taking its coefficients with it, so the two
communities are not guaranteed to match. A period whose plant or agreement no longer resolves
answers `false`.

Every response that embeds `PartitionCoefficientResponse` carries it:
`GET /supplies/{supplyId}/partition-coefficients`, `…/partition-coefficients/active`,
`GET /plants/{plantId}/partition-coefficients/active`, and the replace, activate, deactivate, close
and reopen endpoints under `/plants/{plantId}/sharing-agreements/{sharingAgreementId}/partition-coefficients`.
On the plant and write endpoints it is effectively always `true` — they already demand the same
admin rule — but it is computed, not assumed, and its cost is pinned like any other.

**What depends on it.** conluz-web links each period to
`/production/{plantId}/sharing-agreements/{sharingAgreementId}`, a route guarded client-side on the
plant's `canListSharingAgreements`, not on this capability's guard. The link is safe to show only
while **`canReadSharingAgreement` ⇒ `canListSharingAgreements`**. That holds today because both
reduce to "enabled community admin of the plant's community", and
`CapabilityGuardEquivalenceTest.readingACoefficientsSharingAgreementImpliesListingThePlantsAgreements`
asserts it over the caller matrix. Whoever splits those rules must keep the implication, or have the
web gate the link on both capabilities.

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

`canListSupplies` predicts whether `GET /users/{userId}/supplies` is **allowed**, and it predicts
that exactly: the guard behind it is unchanged by what the listing returns. What the listing is
**scoped to** is a separate question, answered by `SupplyAccessPolicy.visibleSuppliesOwnedBy` — the
set form of the rule `GET /supplies/{supplyId}` applies one supply at a time. The user themselves
receives every supply they own; anyone else receives only those in the communities they administer.
So an admin of A, listing a member of A and B, gets A's supplies and none of B's, and every supply
the listing omits is one `GET /supplies/{supplyId}` would answer 404 on for them (#326). Being let
through by the guard never widens what comes back.

`GET /users` follows the same split. `platform.canListUsers` predicts whether it is **allowed**; what
it is **scoped to** is `UserAccessPolicy.visibleUsers`, the set form of the rule
`GET /users/{userId}` applies one user at a time. A platform admin receives every user; anyone else
receives themselves and the users with an enabled membership in a community they **administer** —
not those of a community they merely belong to. So an admin of A who is a plain member of B gets A's
users and none of B's, and every user the listing omits is one `GET /users/{userId}` would answer 404
on for them (#336).

The same scope bounds what each row's `memberships` map carries: everything for a platform admin and
for the caller's own row; otherwise only the memberships in communities the caller administers —
what `GET /communities/{communityId}/memberships` would show them. A row no longer reveals a user's
role in a community the caller has nothing to administer. The capabilities on the row are assembled
from the user's **full** memberships *before* the response narrows them, so the narrowing cannot move
any of them.

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
| `visibleSuppliesOfUser` | As above: it bounds which of a user's supplies `GET /users/{userId}/supplies` returns, after `canListSuppliesOfUser` — reported as `user.canListSupplies` — has let the request through. |
| `visibleUsers` | As above: it bounds which users `GET /users` returns, and which of their memberships each row carries, after `canListUsers` — reported as `platform.canListUsers` — has let the request through. |
| `isCurrentUser` | A fact the client already has. The rules that care about it fold it in and are reported themselves. |

### Capabilities with no guard

`community.canCreatePlants` has none: creating a plant always names a supply, so no endpoint asks
the question community-wide. The community reports it anyway, because a client has to decide whether
to offer the action before any supply is chosen. It is backed by `PlantAccessPolicy.canCreateIn`,
which is the community-admin half of `canCreate`, and a policy test pins the two together.

A `true` here is necessary but not sufficient for any particular supply —
`SupplyCapabilitiesResponse.canCreatePlant` answers that.

`platform.canAdministerPlatform` has none either, for a different reason: no endpoint asks the
question at all. It gates a surface rather than an action, and every endpoint behind that surface
reports its own decision — the client needs a name for "may I open this at all", which is what this
is. It reads `PlatformAccessPolicy.canAdministerPlatform` directly, the same rule the platform guards
adapt.

Having no guard means `CapabilityGuardEquivalenceTest` cannot cover it, and asserting it against
`PlatformAccessPolicy.canAdministerPlatform` would only re-run the line the assembler itself runs. So
its anchor is an assertion in `MembershipAndPlatformCapabilitiesAssemblerTest` that it equals the
`isPlatformAdmin` flag, over the whole caller matrix. That assertion hard-codes today's rule on
purpose: if `canAdministerPlatform` ever stops being `isPlatformAdmin`, it must be **changed
deliberately** as part of deciding what the rule now is, never adjusted to whatever the code has
started returning.

## How to add a capability

1. **Put the rule in a policy.** `..admin.community.access.policy..`, returning an `AccessDecision`.
   Never in a guard, never in an assembler — `AccessPolicyPurityArchTest` and the split exist so the
   same rule can be evaluated in front of one request and over a page.
2. **Give the endpoint a guard method** that adapts the policy, and reference it from a single
   `@PreAuthorize` (`PreAuthorizeShapeArchTest` allows exactly one call).
3. **Add the field** to the resource's `*CapabilitiesResponse`, as a `boolean` listed in the
   class-level `@Schema(requiredProperties = {...})` with a `description`. Use the builder.
4. **Assemble it** in the resource's assembler, from the policy, mapping `ALLOWED` to `true`. If the
   rule needs data the entity does not carry, load it **once per page** — in the controller, or in an
   assembler method that takes the whole page — never per item. If a client will use the capability
   to decide something another guard enforces (a client-side route, say), assert that implication
   too.
5. **Record it** in `CapabilityInventory`: map the guard to `(resource, field)`, or mark the guard
   `SERVER_ONLY` with the reason, or add the capability to `withoutGuard()`. The build fails until
   you do.
6. **Extend the equivalence test** so the new field is computed both ways, and the **HTTP test** for
   that resource so a client's view of it is asserted. A capability with **no guard** has nothing to
   be equal to, so it is omitted there — say so in a comment, and give it an anchor in its assembler
   test that does not merely re-run the assembler's own line.
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
| `CoefficientWriteQueryCountTest` | The write endpoints returning coefficients build their response at a fixed cost. |
| `*CapabilitiesAssemblerTest` | The rules themselves, per assembler. |
| `*CapabilitiesEndpointTest` | What a client actually receives, per resource. |
| `OpenApiSnapshotTest` | The published contract, so a schema change is visible in review. |
