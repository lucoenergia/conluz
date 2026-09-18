package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

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
 * Callers and targets for the assembler tests. Assemblers evaluate policies over objects they are
 * handed, so — apart from the memberships repository — there is nothing to mock here either.
 */
final class CapabilityFixtures {

    private CapabilityFixtures() {
    }

    static Community community() {
        return CommunityMother.random().build();
    }

    static User platformAdmin() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        return user;
    }

    static User stranger() {
        return UserMother.randomUser();
    }

    static User adminOf(Community community) {
        return withMembership(UserMother.randomUser(), community, CommunityRole.COMMUNITY_ADMIN, true);
    }

    static User memberOf(Community community) {
        return withMembership(UserMother.randomUser(), community, CommunityRole.COMMUNITY_MEMBER, true);
    }

    static User disabledMemberOf(Community community) {
        return withMembership(UserMother.randomUser(), community, CommunityRole.COMMUNITY_MEMBER, false);
    }

    static User withMembership(User user, Community community, CommunityRole role, boolean enabled) {
        user.setMemberships(List.of(membershipOf(user, community, role, enabled)));
        return user;
    }

    static CommunityMembership membershipOf(User user, Community community, CommunityRole role, boolean enabled) {
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community)
                .withRole(role)
                .withEnabled(enabled)
                .build();
    }

    /**
     * A user with no memberships at all, which is what a mapper produces and what a user who
     * genuinely belongs to no community also looks like — the very ambiguity
     * {@link UserCapabilitiesAssembler} refuses to resolve by guessing.
     */
    static User userWithNoMemberships() {
        User user = UserMother.randomUser();
        user.setMemberships(List.of());
        return user;
    }

    static Supply supplyIn(Community community, User owner) {
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withCommunity(community)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }

    static Plant plantOf(Supply supply) {
        return new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
    }

    static SharingAgreement agreementOf(Plant plant) {
        return new SharingAgreement.Builder().withId(UUID.randomUUID()).withPlantId(plant.getId()).build();
    }
}
