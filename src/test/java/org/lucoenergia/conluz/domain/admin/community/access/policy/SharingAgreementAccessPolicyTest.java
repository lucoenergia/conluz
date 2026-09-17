package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * These rules assume the plant is already known to be visible — that is
 * {@link PlantAccessPolicy}'s decision — and speak only about the agreement.
 */
class SharingAgreementAccessPolicyTest {

    private final SharingAgreementAccessPolicy policy = new SharingAgreementAccessPolicy();

    // --- canManage ---

    @Test
    void canManage_allows_anEnabledCommunityAdminOfThePlantCommunity() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.ALLOWED, policy.canManage(PolicyFixtures.adminOf(community), plant,
                PolicyFixtures.agreement(UUID.randomUUID(), plant.getId())));
    }

    @Test
    void canManage_forbids_aPlainMemberOfThePlantCommunity() {
        // Agreement contents are admin-only, and a member can see the plant -> 403.
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.FORBIDDEN, policy.canManage(PolicyFixtures.memberOf(community), plant,
                PolicyFixtures.agreement(UUID.randomUUID(), plant.getId())));
    }

    @Test
    void canManage_isNotVisible_whenTheAgreementDoesNotExist() {
        Community community = PolicyFixtures.community();

        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManage(PolicyFixtures.adminOf(community), plantIn(community), null));
    }

    @Test
    void canManage_isNotVisible_whenTheAgreementBelongsToAnotherPlant() {
        // It exists, but the caller must not learn that it exists elsewhere.
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.NOT_VISIBLE, policy.canManage(PolicyFixtures.adminOf(community), plant,
                PolicyFixtures.agreement(UUID.randomUUID(), UUID.randomUUID())));
    }

    @Test
    void canManage_isNotVisible_whenTheAgreementCarriesNoPlantId() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.NOT_VISIBLE, policy.canManage(PolicyFixtures.adminOf(community), plant,
                PolicyFixtures.agreement(UUID.randomUUID(), null)));
    }

    @Test
    void canManage_forbids_aPlatformAdminWhoDoesNotAdministerThePlantCommunity() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.FORBIDDEN, policy.canManage(PolicyFixtures.platformAdmin(), plant,
                PolicyFixtures.agreement(UUID.randomUUID(), plant.getId())));
    }

    @Test
    void canManage_forbids_whenTheAdminMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.FORBIDDEN, policy.canManage(PolicyFixtures.disabledAdminOf(community), plant,
                PolicyFixtures.agreement(UUID.randomUUID(), plant.getId())));
    }

    // --- canRead ---
    // Byte-for-byte canManage today; kept separate so the two can diverge later.

    @Test
    void canRead_matchesCanManage_forEveryCaller() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);
        SharingAgreement ours = PolicyFixtures.agreement(UUID.randomUUID(), plant.getId());
        SharingAgreement theirs = PolicyFixtures.agreement(UUID.randomUUID(), UUID.randomUUID());
        User admin = PolicyFixtures.adminOf(community);
        User member = PolicyFixtures.memberOf(community);

        assertEquals(policy.canManage(admin, plant, ours), policy.canRead(admin, plant, ours));
        assertEquals(policy.canManage(member, plant, ours), policy.canRead(member, plant, ours));
        assertEquals(policy.canManage(admin, plant, theirs), policy.canRead(admin, plant, theirs));
        assertEquals(policy.canManage(admin, plant, null), policy.canRead(admin, plant, null));
    }

    @Test
    void canRead_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.ALLOWED, policy.canRead(PolicyFixtures.adminOf(community), plant,
                PolicyFixtures.agreement(UUID.randomUUID(), plant.getId())));
    }

    @Test
    void canRead_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.FORBIDDEN, policy.canRead(PolicyFixtures.memberOf(community), plant,
                PolicyFixtures.agreement(UUID.randomUUID(), plant.getId())));
    }

    private Plant plantIn(Community community) {
        return PolicyFixtures.plantOf(PolicyFixtures.supplyIn(community, UUID.randomUUID()));
    }
}
