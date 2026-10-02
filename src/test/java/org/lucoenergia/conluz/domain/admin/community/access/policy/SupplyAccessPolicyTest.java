package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.SupplyOwnerScope;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyAccessPolicyTest {

    private final SupplyAccessPolicy policy = new SupplyAccessPolicy();

    // --- canRead ---
    // Reading and editing need the same access, so anyone who may not read gets not-visible, never
    // forbidden: a 403 would confirm the supply exists.

    @Test
    void canRead_allows_theOwner() {
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.ALLOWED,
                policy.canRead(owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    @Test
    void canRead_allows_anEnabledCommunityAdminOfTheSupplyCommunity() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canRead(PolicyFixtures.adminOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canRead_isNotVisible_forAPlainMemberOfTheSupplyCommunity() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.memberOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canRead_isNotVisible_forAPlatformAdminWhoNeitherOwnsNorAdministers() {
        // No platform-admin bypass anywhere in supply access.
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.platformAdmin(),
                PolicyFixtures.supplyIn(PolicyFixtures.community(), UUID.randomUUID())));
    }

    @Test
    void canRead_isNotVisible_whenTheSupplyDoesNotExist() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canRead_isNotVisible_whenTheAdminMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.disabledAdminOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    // --- canEdit ---

    @Test
    void canEdit_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canEdit(PolicyFixtures.adminOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canEdit_forbids_theOwnerWhoIsNotACommunityAdmin() {
        // The owner can see the supply, so this is the one supply denial that is a 403.
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canEdit(owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    @Test
    void canEdit_allows_anOwnerWhoIsAlsoTheCommunityAdmin() {
        Community community = PolicyFixtures.community();
        User owner = PolicyFixtures.adminOf(community);
        assertEquals(AccessDecision.ALLOWED,
                policy.canEdit(owner, PolicyFixtures.supplyIn(community, owner.getId())));
    }

    @Test
    void canEdit_isNotVisible_forAPlainMemberOfTheSupplyCommunity() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canEdit(PolicyFixtures.memberOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canEdit_isNotVisible_whenTheSupplyDoesNotExist() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canEdit(PolicyFixtures.platformAdmin(), null));
    }

    // --- canReadPartitionCoefficients ---
    // Byte-for-byte canRead today; kept separate so the two can diverge later.

    @Test
    void canReadPartitionCoefficients_matchesCanRead_forEveryCaller() {
        Community community = PolicyFixtures.community();
        User admin = PolicyFixtures.adminOf(community);
        User member = PolicyFixtures.memberOf(community);
        User owner = PolicyFixtures.stranger();
        Supply supplyInCommunity = PolicyFixtures.supplyIn(community, UUID.randomUUID());
        Supply ownedSupply = PolicyFixtures.supplyWithoutCommunity(owner.getId());

        assertEquals(policy.canRead(admin, supplyInCommunity),
                policy.canReadPartitionCoefficients(admin, supplyInCommunity));
        assertEquals(policy.canRead(member, supplyInCommunity),
                policy.canReadPartitionCoefficients(member, supplyInCommunity));
        assertEquals(policy.canRead(owner, ownedSupply),
                policy.canReadPartitionCoefficients(owner, ownedSupply));
        assertEquals(policy.canRead(admin, null), policy.canReadPartitionCoefficients(admin, null));
    }

    @Test
    void canReadPartitionCoefficients_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canReadPartitionCoefficients(
                PolicyFixtures.adminOf(community), PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canReadPartitionCoefficients_allows_theOwnerWhoIsNotACommunityAdmin() {
        // The coefficients describe the owner's own share; hiding pending periods is the read's
        // job, not the guard's.
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.ALLOWED, policy.canReadPartitionCoefficients(
                owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    @Test
    void canReadPartitionCoefficients_isNotVisible_forAPlainMemberOfTheSupplyCommunity() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canReadPartitionCoefficients(
                PolicyFixtures.memberOf(community), PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    // --- isCommunityAdminOfSupply ---
    // A shaping question asked after a gate has passed, so it never answers not-visible.

    @Test
    void isCommunityAdminOfSupply_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.isCommunityAdminOfSupply(
                PolicyFixtures.adminOf(community), PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void isCommunityAdminOfSupply_allows_anOwnerWhoIsAlsoTheCommunityAdmin() {
        Community community = PolicyFixtures.community();
        User owner = PolicyFixtures.adminOf(community);
        assertEquals(AccessDecision.ALLOWED,
                policy.isCommunityAdminOfSupply(owner, PolicyFixtures.supplyIn(community, owner.getId())));
    }

    @Test
    void isCommunityAdminOfSupply_forbids_theOwnerAlone() {
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.FORBIDDEN, policy.isCommunityAdminOfSupply(
                owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    @Test
    void isCommunityAdminOfSupply_forbids_aPlatformAdminWhoDoesNotAdministerTheCommunity() {
        assertEquals(AccessDecision.FORBIDDEN, policy.isCommunityAdminOfSupply(
                PolicyFixtures.platformAdmin(), PolicyFixtures.supplyIn(PolicyFixtures.community(), UUID.randomUUID())));
    }

    @Test
    void isCommunityAdminOfSupply_forbids_whenTheSupplyDoesNotExist() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.isCommunityAdminOfSupply(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void isCommunityAdminOfSupply_forbids_whenTheSupplyHasNoCommunity() {
        assertEquals(AccessDecision.FORBIDDEN, policy.isCommunityAdminOfSupply(
                PolicyFixtures.adminOf(PolicyFixtures.community()),
                PolicyFixtures.supplyWithoutCommunity(UUID.randomUUID())));
    }

    // --- the shared predicates ---

    @Test
    void isVisible_isFalse_forAMissingSupply() {
        assertFalse(policy.isVisible(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void isOwner_comparesTheSupplyOwnerToTheCaller() {
        User owner = PolicyFixtures.stranger();
        assertTrue(policy.isOwner(owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
        assertFalse(policy.isOwner(owner, PolicyFixtures.supplyWithoutCommunity(UUID.randomUUID())));
    }

    @Test
    void isOwner_isFalse_whenTheSupplyHasNoOwnerId() {
        assertFalse(policy.isOwner(PolicyFixtures.stranger(), PolicyFixtures.supplyWithoutCommunity(null)));
    }

    // --- visibleSuppliesOwnedBy ---
    // The set form of isVisible for one owner's supplies. The equivalence test below is what makes
    // "a listing never carries a supply its own GET would 404 on" a property of the policy.

    @Test
    void visibleSuppliesOwnedBy_isEverything_forTheOwnerThemselves() {
        User owner = PolicyFixtures.memberOf(PolicyFixtures.community());
        assertEquals(SupplyOwnerScope.all(), policy.visibleSuppliesOwnedBy(owner, owner.getId()));
    }

    @Test
    void visibleSuppliesOwnedBy_isTheAdministeredCommunity_forAnAdminOfOneCommunity() {
        Community a = PolicyFixtures.community();
        assertEquals(SupplyOwnerScope.inCommunities(Set.of(a.getId())),
                policy.visibleSuppliesOwnedBy(PolicyFixtures.adminOf(a), UUID.randomUUID()));
    }

    @Test
    void visibleSuppliesOwnedBy_isEveryAdministeredCommunity_forAnAdminOfSeveral() {
        Community a = PolicyFixtures.community();
        Community b = PolicyFixtures.community();
        assertEquals(SupplyOwnerScope.inCommunities(Set.of(a.getId(), b.getId())),
                policy.visibleSuppliesOwnedBy(adminOfBoth(a, b), UUID.randomUUID()));
    }

    @Test
    void visibleSuppliesOwnedBy_isEmpty_forAPlatformAdminWhoAdministersNothing() {
        // No platform-admin bypass, as in isVisible.
        assertEquals(SupplyOwnerScope.inCommunities(Set.of()),
                policy.visibleSuppliesOwnedBy(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void visibleSuppliesOwnedBy_isEmpty_forAnAbsentCaller() {
        assertEquals(SupplyOwnerScope.inCommunities(Set.of()),
                policy.visibleSuppliesOwnedBy(null, UUID.randomUUID()));
    }

    @Test
    void visibleSuppliesOwnedBy_includesExactlyTheSuppliesIsVisibleAllows() {
        Community a = PolicyFixtures.community();
        Community b = PolicyFixtures.community();
        Community c = PolicyFixtures.community();
        User owner = PolicyFixtures.withMembership(PolicyFixtures.stranger(), a, CommunityRole.COMMUNITY_MEMBER, true);
        UUID ownerId = owner.getId();

        Map<String, User> callers = new LinkedHashMap<>();
        callers.put("the owner", owner);
        callers.put("admin of A", PolicyFixtures.adminOf(a));
        callers.put("admin of A and B", adminOfBoth(a, b));
        callers.put("admin of C only", PolicyFixtures.adminOf(c));
        callers.put("plain member of A", PolicyFixtures.memberOf(a));
        callers.put("disabled admin of A", PolicyFixtures.disabledAdminOf(a));
        callers.put("platform admin", PolicyFixtures.platformAdmin());
        callers.put("platform admin, member of A", PolicyFixtures.platformAdminMemberOf(a));
        callers.put("stranger", PolicyFixtures.stranger());

        Map<String, Supply> supplies = new LinkedHashMap<>();
        supplies.put("in A", PolicyFixtures.supplyIn(a, ownerId));
        supplies.put("in B", PolicyFixtures.supplyIn(b, ownerId));
        supplies.put("in no community", PolicyFixtures.supplyWithoutCommunity(ownerId));

        int included = 0;
        int excluded = 0;
        for (Map.Entry<String, User> caller : callers.entrySet()) {
            SupplyOwnerScope scope = policy.visibleSuppliesOwnedBy(caller.getValue(), ownerId);
            for (Map.Entry<String, Supply> supply : supplies.entrySet()) {
                boolean visible = policy.isVisible(caller.getValue(), supply.getValue());
                assertEquals(visible, scope.includes(supply.getValue()),
                        caller.getKey() + ", supply " + supply.getKey() + ": scope " + scope
                                + " disagrees with isVisible=" + visible);
                if (visible) included++; else excluded++;
            }
        }
        // Both outcomes must occur, or the agreement above proves nothing.
        assertTrue(included > 0 && excluded > 0, "included=" + included + ", excluded=" + excluded);
    }

    private static User adminOfBoth(Community a, Community b) {
        User user = PolicyFixtures.stranger();
        user.setMemberships(List.of(
                PolicyFixtures.membershipIn(a, CommunityRole.COMMUNITY_ADMIN, true),
                PolicyFixtures.membershipIn(b, CommunityRole.COMMUNITY_ADMIN, true)));
        return user;
    }
}
