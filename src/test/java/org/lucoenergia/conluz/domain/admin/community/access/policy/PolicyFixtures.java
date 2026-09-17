package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.List;
import java.util.UUID;

/**
 * Callers and targets for the policy tests. Policies are pure, so every fixture here is a plain
 * object — there is nothing to mock.
 */
final class PolicyFixtures {

    private PolicyFixtures() {
    }

    static User platformAdmin() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        return user;
    }

    static User stranger() {
        return UserMother.randomUser();
    }

    static User memberOf(Community community) {
        return withMembership(UserMother.randomUser(), community, CommunityRole.COMMUNITY_MEMBER, true);
    }

    static User adminOf(Community community) {
        return withMembership(UserMother.randomUser(), community, CommunityRole.COMMUNITY_ADMIN, true);
    }

    static User disabledAdminOf(Community community) {
        return withMembership(UserMother.randomUser(), community, CommunityRole.COMMUNITY_ADMIN, false);
    }

    static User disabledMemberOf(Community community) {
        return withMembership(UserMother.randomUser(), community, CommunityRole.COMMUNITY_MEMBER, false);
    }

    static User platformAdminMemberOf(Community community) {
        User user = withMembership(UserMother.randomUser(), community, CommunityRole.COMMUNITY_MEMBER, true);
        user.setPlatformAdmin(true);
        return user;
    }

    static User withMembership(User user, Community community, CommunityRole role, boolean enabled) {
        user.setMemberships(List.of(new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community)
                .withRole(role)
                .withEnabled(enabled)
                .build()));
        return user;
    }

    static CommunityMembership membershipIn(Community community, CommunityRole role, boolean enabled) {
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(community)
                .withRole(role)
                .withEnabled(enabled)
                .build();
    }

    static Community community() {
        return CommunityMother.random().build();
    }

    static Supply supplyIn(Community community, UUID ownerId) {
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(new User.Builder().id(ownerId).build())
                .withCommunity(community)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }

    static Supply supplyWithoutCommunity(UUID ownerId) {
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(new User.Builder().id(ownerId).build())
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }

    static Plant plantOf(Supply supply) {
        return new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
    }

    static SharingAgreement agreement(UUID agreementId, UUID plantId) {
        return new SharingAgreement.Builder().withId(agreementId).withPlantId(plantId).build();
    }
}
