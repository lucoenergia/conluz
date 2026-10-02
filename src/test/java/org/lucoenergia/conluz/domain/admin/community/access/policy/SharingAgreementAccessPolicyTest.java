package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These rules assume the plant is already known to be visible — that is
 * {@link PlantAccessPolicy}'s decision — and speak only about the agreement.
 */
class SharingAgreementAccessPolicyTest {

    private final SharingAgreementAccessPolicy policy =
            new SharingAgreementAccessPolicy(new PlantAccessPolicy(new SupplyAccessPolicy()));

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

    // --- canReadThroughPlant / canManageThroughPlant ---
    // The whole decision an endpoint makes: the plant first, then the agreement. A guard splits the
    // two apart again only to pick which *NotFoundException to throw; the rule lives here.

    @Test
    void throughPlant_allows_anEnabledCommunityAdminOfThePlantCommunity() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);
        User admin = PolicyFixtures.adminOf(community);
        SharingAgreement agreement = PolicyFixtures.agreement(UUID.randomUUID(), plant.getId());

        assertEquals(AccessDecision.ALLOWED, policy.canReadThroughPlant(admin, plant, agreement));
        assertEquals(AccessDecision.ALLOWED, policy.canManageThroughPlant(admin, plant, agreement));
    }

    /**
     * A plain member can see the plant, so the agreement rule gets to run and answers forbidden.
     * This is the case that distinguishes the composed decision from the plant gate alone.
     */
    @Test
    void throughPlant_isForbidden_forAPlainMemberWhoCanSeeThePlant() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);
        User member = PolicyFixtures.memberOf(community);
        SharingAgreement agreement = PolicyFixtures.agreement(UUID.randomUUID(), plant.getId());

        assertEquals(AccessDecision.FORBIDDEN, policy.canReadThroughPlant(member, plant, agreement));
        assertEquals(AccessDecision.FORBIDDEN, policy.canManageThroughPlant(member, plant, agreement));
    }

    @Test
    void throughPlant_isNotVisible_whenTheCallerCannotSeeThePlant() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);
        User stranger = PolicyFixtures.stranger();
        SharingAgreement agreement = PolicyFixtures.agreement(UUID.randomUUID(), plant.getId());

        assertEquals(AccessDecision.NOT_VISIBLE, policy.canReadThroughPlant(stranger, plant, agreement));
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canManageThroughPlant(stranger, plant, agreement));
    }

    /**
     * A platform admin who is not a member cannot see a plant at all, so the agreement behind it is
     * not-visible too -- there is no platform-admin bypass on this path.
     */
    @Test
    void throughPlant_isNotVisible_forANonMemberPlatformAdmin() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);
        SharingAgreement agreement = PolicyFixtures.agreement(UUID.randomUUID(), plant.getId());

        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canReadThroughPlant(PolicyFixtures.platformAdmin(), plant, agreement));
    }

    @Test
    void throughPlant_isNotVisible_whenTheAgreementBelongsToAnotherPlant() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);
        User admin = PolicyFixtures.adminOf(community);

        assertEquals(AccessDecision.NOT_VISIBLE, policy.canReadThroughPlant(admin, plant,
                PolicyFixtures.agreement(UUID.randomUUID(), UUID.randomUUID())));
    }

    @Test
    void throughPlant_isNotVisible_whenTheAgreementDoesNotExist() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageThroughPlant(PolicyFixtures.adminOf(community), plant, null));
    }

    /**
     * The two not-visible outcomes above are indistinguishable in the decision, which is exactly why
     * a guard needs this to choose between a missing plant and a missing agreement.
     */
    @Test
    void isPlantVisible_separatesAMissingPlantFromAMissingAgreement() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);

        assertTrue(policy.isPlantVisible(PolicyFixtures.adminOf(community), plant));
        assertFalse(policy.isPlantVisible(PolicyFixtures.stranger(), plant));
        assertFalse(policy.isPlantVisible(PolicyFixtures.adminOf(community), null));
    }

    private Plant plantIn(Community community) {
        return PolicyFixtures.plantOf(PolicyFixtures.supplyIn(community, UUID.randomUUID()));
    }
}
