package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallerMembershipsTest {

    // --- hasCommunityAdminRoleIn ---

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenCommunityIdIsNull() {
        assertFalse(CallerMemberships.hasCommunityAdminRoleIn(UserMother.randomUser(), null));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertFalse(CallerMemberships.hasCommunityAdminRoleIn(user, UUID.randomUUID()));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsTrue_whenUserHasEnabledAdminRole() {
        Community community = PolicyFixtures.community();
        assertTrue(CallerMemberships.hasCommunityAdminRoleIn(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenRoleIsMemberNotAdmin() {
        Community community = PolicyFixtures.community();
        assertFalse(CallerMemberships.hasCommunityAdminRoleIn(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertFalse(CallerMemberships.hasCommunityAdminRoleIn(
                PolicyFixtures.disabledAdminOf(community), community.getId()));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenUserAdministersAnotherCommunity() {
        assertFalse(CallerMemberships.hasCommunityAdminRoleIn(
                PolicyFixtures.adminOf(PolicyFixtures.community()), UUID.randomUUID()));
    }

    // --- hasMembershipInCommunity ---

    @Test
    void hasMembershipInCommunity_returnsFalse_whenCommunityIdIsNull() {
        assertFalse(CallerMemberships.hasMembershipInCommunity(UserMother.randomUser(), null));
    }

    @Test
    void hasMembershipInCommunity_returnsTrue_whenUserHasEnabledMembership() {
        Community community = PolicyFixtures.community();
        assertTrue(CallerMemberships.hasMembershipInCommunity(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void hasMembershipInCommunity_returnsTrue_forAnEnabledAdminMembershipToo() {
        Community community = PolicyFixtures.community();
        assertTrue(CallerMemberships.hasMembershipInCommunity(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void hasMembershipInCommunity_returnsFalse_whenMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertFalse(CallerMemberships.hasMembershipInCommunity(
                PolicyFixtures.disabledMemberOf(community), community.getId()));
    }

    // --- canSeeCommunity ---

    @Test
    void canSeeCommunity_returnsFalse_whenUserIsNull() {
        assertFalse(CallerMemberships.canSeeCommunity(null, UUID.randomUUID()));
    }

    @Test
    void canSeeCommunity_returnsTrue_whenUserIsPlatformAdmin() {
        assertTrue(CallerMemberships.canSeeCommunity(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canSeeCommunity_returnsTrue_whenPlatformAdminAndCommunityIdIsNull() {
        assertTrue(CallerMemberships.canSeeCommunity(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canSeeCommunity_returnsTrue_whenUserHasEnabledMembership() {
        Community community = PolicyFixtures.community();
        assertTrue(CallerMemberships.canSeeCommunity(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canSeeCommunity_returnsFalse_whenMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertFalse(CallerMemberships.canSeeCommunity(PolicyFixtures.disabledAdminOf(community), community.getId()));
    }

    @Test
    void canSeeCommunity_returnsFalse_whenUserIsNotAMember() {
        assertFalse(CallerMemberships.canSeeCommunity(PolicyFixtures.stranger(), UUID.randomUUID()));
    }

    @Test
    void canSeeCommunity_returnsFalse_whenCommunityIdIsNullAndUserIsNotPlatformAdmin() {
        assertFalse(CallerMemberships.canSeeCommunity(PolicyFixtures.stranger(), null));
    }

    // --- isCurrentUser ---

    @Test
    void isCurrentUser_returnsTrue_whenIdsMatch() {
        User user = UserMother.randomUser();
        assertTrue(CallerMemberships.isCurrentUser(user, user.getId()));
    }

    @Test
    void isCurrentUser_returnsFalse_whenIdsDontMatch() {
        assertFalse(CallerMemberships.isCurrentUser(UserMother.randomUser(), UUID.randomUUID()));
    }

    @Test
    void isCurrentUser_returnsFalse_whenUserIsNull() {
        assertFalse(CallerMemberships.isCurrentUser(null, UUID.randomUUID()));
    }

    @Test
    void isCurrentUser_returnsFalse_whenUserIdIsNull() {
        assertFalse(CallerMemberships.isCurrentUser(UserMother.randomUser(), null));
    }

    // --- isPlatformAdmin ---

    @Test
    void isPlatformAdmin_returnsFalse_whenUserIsNull() {
        assertFalse(CallerMemberships.isPlatformAdmin(null));
    }

    @Test
    void isPlatformAdmin_returnsFalse_whenFlagWasNeverSet() {
        assertFalse(CallerMemberships.isPlatformAdmin(UserMother.randomUser()));
    }

    @Test
    void isPlatformAdmin_returnsTrue_whenFlagIsSet() {
        assertTrue(CallerMemberships.isPlatformAdmin(PolicyFixtures.platformAdmin()));
    }

    // --- hasAnyEnabledCommunityAdminRole ---

    @Test
    void hasAnyEnabledCommunityAdminRole_returnsFalse_whenUserIsNull() {
        assertFalse(CallerMemberships.hasAnyEnabledCommunityAdminRole(null));
    }

    @Test
    void hasAnyEnabledCommunityAdminRole_returnsFalse_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertFalse(CallerMemberships.hasAnyEnabledCommunityAdminRole(user));
    }

    @Test
    void hasAnyEnabledCommunityAdminRole_returnsTrue_whenAnyEnabledAdminMembershipExists() {
        assertTrue(CallerMemberships.hasAnyEnabledCommunityAdminRole(
                PolicyFixtures.adminOf(PolicyFixtures.community())));
    }

    @Test
    void hasAnyEnabledCommunityAdminRole_returnsFalse_whenOnlyMemberMembershipsExist() {
        assertFalse(CallerMemberships.hasAnyEnabledCommunityAdminRole(
                PolicyFixtures.memberOf(PolicyFixtures.community())));
    }

    @Test
    void hasAnyEnabledCommunityAdminRole_returnsFalse_whenAdminMembershipIsDisabled() {
        assertFalse(CallerMemberships.hasAnyEnabledCommunityAdminRole(
                PolicyFixtures.disabledAdminOf(PolicyFixtures.community())));
    }

    @Test
    void hasAnyEnabledCommunityAdminRole_returnsTrue_whenTheAdminMembershipCommunityHasNoId() {
        // Holding the role is the question here, so a community without an id still counts --
        // unlike adminCommunityIds, which has nothing to put in the set.
        User user = UserMother.randomUser();
        PolicyFixtures.withMembership(user, CommunityMother.random().withId(null).build(),
                CommunityRole.COMMUNITY_ADMIN, true);

        assertTrue(CallerMemberships.hasAnyEnabledCommunityAdminRole(user));
        assertEquals(Set.of(), CallerMemberships.adminCommunityIds(user));
    }

    // --- adminCommunityIds ---

    @Test
    void adminCommunityIds_returnsEmpty_whenUserIsNull() {
        assertEquals(Set.of(), CallerMemberships.adminCommunityIds(null));
    }

    @Test
    void adminCommunityIds_returnsEmpty_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertEquals(Set.of(), CallerMemberships.adminCommunityIds(user));
    }

    @Test
    void adminCommunityIds_returnsEmpty_whenUserIsPlatformAdminButIsNotAdminOfAnyCommunity() {
        // Administering the platform is not administering a community.
        assertEquals(Set.of(), CallerMemberships.adminCommunityIds(PolicyFixtures.platformAdmin()));
    }

    @Test
    void adminCommunityIds_returnsOnlyAdminCommunities() {
        Community administered = PolicyFixtures.community();
        Community joined = PolicyFixtures.community();
        User user = UserMother.randomUser();
        user.setMemberships(List.of(
                PolicyFixtures.membershipIn(administered, CommunityRole.COMMUNITY_ADMIN, true),
                PolicyFixtures.membershipIn(joined, CommunityRole.COMMUNITY_MEMBER, true)));

        assertEquals(Set.of(administered.getId()), CallerMemberships.adminCommunityIds(user));
    }

    @Test
    void adminCommunityIds_excludesDisabledAdminMemberships() {
        assertEquals(Set.of(), CallerMemberships.adminCommunityIds(
                PolicyFixtures.disabledAdminOf(PolicyFixtures.community())));
    }

    // --- enabledMembershipCommunityIds ---

    @Test
    void enabledMembershipCommunityIds_returnsEmpty_whenUserIsNull() {
        assertEquals(Set.of(), CallerMemberships.enabledMembershipCommunityIds(null));
    }

    @Test
    void enabledMembershipCommunityIds_returnsEmpty_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertEquals(Set.of(), CallerMemberships.enabledMembershipCommunityIds(user));
    }

    @Test
    void enabledMembershipCommunityIds_returnsEveryEnabledCommunityWhateverTheRole() {
        Community administered = PolicyFixtures.community();
        Community joined = PolicyFixtures.community();
        User user = UserMother.randomUser();
        user.setMemberships(List.of(
                PolicyFixtures.membershipIn(administered, CommunityRole.COMMUNITY_ADMIN, true),
                PolicyFixtures.membershipIn(joined, CommunityRole.COMMUNITY_MEMBER, true)));

        assertEquals(Set.of(administered.getId(), joined.getId()),
                CallerMemberships.enabledMembershipCommunityIds(user));
    }

    @Test
    void enabledMembershipCommunityIds_excludesDisabledMemberships() {
        assertEquals(Set.of(), CallerMemberships.enabledMembershipCommunityIds(
                PolicyFixtures.disabledMemberOf(PolicyFixtures.community())));
    }

    // --- enabledCommunityIds ---

    @Test
    void enabledCommunityIds_returnsEmpty_whenTheListIsNull() {
        assertEquals(Set.of(), CallerMemberships.enabledCommunityIds(null));
    }

    @Test
    void enabledCommunityIds_skipsMembershipsWithoutACommunity() {
        assertEquals(Set.of(), CallerMemberships.enabledCommunityIds(
                List.of(PolicyFixtures.membershipIn(null, CommunityRole.COMMUNITY_MEMBER, true))));
    }

    @Test
    void enabledCommunityIds_keepsOnlyEnabledOnes() {
        Community enabled = PolicyFixtures.community();
        Community disabled = PolicyFixtures.community();

        assertEquals(Set.of(enabled.getId()), CallerMemberships.enabledCommunityIds(List.of(
                PolicyFixtures.membershipIn(enabled, CommunityRole.COMMUNITY_MEMBER, true),
                PolicyFixtures.membershipIn(disabled, CommunityRole.COMMUNITY_ADMIN, false))));
    }
}
