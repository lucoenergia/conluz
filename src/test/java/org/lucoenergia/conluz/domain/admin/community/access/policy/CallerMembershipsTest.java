package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("MEM-004 hasCommunityAdminRoleIn is false for a pending admin membership")
    void hasCommunityAdminRoleIn_returnsFalse_whenMembershipIsPending() {
        Community community = PolicyFixtures.community();
        assertFalse(CallerMemberships.hasCommunityAdminRoleIn(
                PolicyFixtures.pendingAdminOf(community), community.getId()));
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

    @Test
    @DisplayName("MEM-004 hasMembershipInCommunity is false for a pending membership")
    void hasMembershipInCommunity_returnsFalse_whenMembershipIsPending() {
        Community community = PolicyFixtures.community();
        assertFalse(CallerMemberships.hasMembershipInCommunity(
                PolicyFixtures.pendingMemberOf(community), community.getId()));
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
    @DisplayName("MEM-004 canSeeCommunity is false for a pending membership")
    void canSeeCommunity_returnsFalse_whenMembershipIsPending() {
        Community community = PolicyFixtures.community();
        assertFalse(CallerMemberships.canSeeCommunity(PolicyFixtures.pendingAdminOf(community), community.getId()));
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

    // --- hasAnyActiveCommunityAdminRole ---

    @Test
    void hasAnyActiveCommunityAdminRole_returnsFalse_whenUserIsNull() {
        assertFalse(CallerMemberships.hasAnyActiveCommunityAdminRole(null));
    }

    @Test
    void hasAnyActiveCommunityAdminRole_returnsFalse_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertFalse(CallerMemberships.hasAnyActiveCommunityAdminRole(user));
    }

    @Test
    void hasAnyActiveCommunityAdminRole_returnsTrue_whenAnyEnabledAdminMembershipExists() {
        assertTrue(CallerMemberships.hasAnyActiveCommunityAdminRole(
                PolicyFixtures.adminOf(PolicyFixtures.community())));
    }

    @Test
    void hasAnyActiveCommunityAdminRole_returnsFalse_whenOnlyMemberMembershipsExist() {
        assertFalse(CallerMemberships.hasAnyActiveCommunityAdminRole(
                PolicyFixtures.memberOf(PolicyFixtures.community())));
    }

    @Test
    void hasAnyActiveCommunityAdminRole_returnsFalse_whenAdminMembershipIsDisabled() {
        assertFalse(CallerMemberships.hasAnyActiveCommunityAdminRole(
                PolicyFixtures.disabledAdminOf(PolicyFixtures.community())));
    }

    @Test
    @DisplayName("MEM-004 hasAnyActiveCommunityAdminRole is false for a pending admin membership")
    void hasAnyActiveCommunityAdminRole_returnsFalse_whenAdminMembershipIsPending() {
        assertFalse(CallerMemberships.hasAnyActiveCommunityAdminRole(
                PolicyFixtures.pendingAdminOf(PolicyFixtures.community())));
    }

    @Test
    void hasAnyActiveCommunityAdminRole_returnsTrue_whenTheAdminMembershipCommunityHasNoId() {
        // Holding the role is the question here, so a community without an id still counts --
        // unlike adminCommunityIds, which has nothing to put in the set.
        User user = UserMother.randomUser();
        PolicyFixtures.withMembership(user, CommunityMother.random().withId(null).build(),
                CommunityRole.COMMUNITY_ADMIN, true);

        assertTrue(CallerMemberships.hasAnyActiveCommunityAdminRole(user));
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

    @Test
    @DisplayName("MEM-004 adminCommunityIds excludes pending admin memberships")
    void adminCommunityIds_excludesPendingAdminMemberships() {
        assertEquals(Set.of(), CallerMemberships.adminCommunityIds(
                PolicyFixtures.pendingAdminOf(PolicyFixtures.community())));
    }

    // --- activeMembershipCommunityIds ---

    @Test
    void activeMembershipCommunityIds_returnsEmpty_whenUserIsNull() {
        assertEquals(Set.of(), CallerMemberships.activeMembershipCommunityIds(null));
    }

    @Test
    void activeMembershipCommunityIds_returnsEmpty_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertEquals(Set.of(), CallerMemberships.activeMembershipCommunityIds(user));
    }

    @Test
    void activeMembershipCommunityIds_returnsEveryEnabledCommunityWhateverTheRole() {
        Community administered = PolicyFixtures.community();
        Community joined = PolicyFixtures.community();
        User user = UserMother.randomUser();
        user.setMemberships(List.of(
                PolicyFixtures.membershipIn(administered, CommunityRole.COMMUNITY_ADMIN, true),
                PolicyFixtures.membershipIn(joined, CommunityRole.COMMUNITY_MEMBER, true)));

        assertEquals(Set.of(administered.getId(), joined.getId()),
                CallerMemberships.activeMembershipCommunityIds(user));
    }

    @Test
    void activeMembershipCommunityIds_excludesDisabledMemberships() {
        assertEquals(Set.of(), CallerMemberships.activeMembershipCommunityIds(
                PolicyFixtures.disabledMemberOf(PolicyFixtures.community())));
    }

    @Test
    @DisplayName("MEM-004 activeMembershipCommunityIds excludes pending memberships")
    void activeMembershipCommunityIds_excludesPendingMemberships() {
        assertEquals(Set.of(), CallerMemberships.activeMembershipCommunityIds(
                PolicyFixtures.pendingMemberOf(PolicyFixtures.community())));
    }

    // --- activeCommunityIds ---

    @Test
    void activeCommunityIds_returnsEmpty_whenTheListIsNull() {
        assertEquals(Set.of(), CallerMemberships.activeCommunityIds(null));
    }

    @Test
    void activeCommunityIds_skipsMembershipsWithoutACommunity() {
        assertEquals(Set.of(), CallerMemberships.activeCommunityIds(
                List.of(PolicyFixtures.membershipIn(null, CommunityRole.COMMUNITY_MEMBER, true))));
    }

    @Test
    void activeCommunityIds_keepsOnlyEnabledOnes() {
        Community enabled = PolicyFixtures.community();
        Community disabled = PolicyFixtures.community();

        assertEquals(Set.of(enabled.getId()), CallerMemberships.activeCommunityIds(List.of(
                PolicyFixtures.membershipIn(enabled, CommunityRole.COMMUNITY_MEMBER, true),
                PolicyFixtures.membershipIn(disabled, CommunityRole.COMMUNITY_ADMIN, false))));
    }

    @Test
    @DisplayName("MEM-004 activeCommunityIds excludes pending memberships")
    void activeCommunityIds_excludesPendingOnes() {
        Community active = PolicyFixtures.community();
        Community pending = PolicyFixtures.community();

        assertEquals(Set.of(active.getId()), CallerMemberships.activeCommunityIds(List.of(
                PolicyFixtures.membershipIn(active, CommunityRole.COMMUNITY_MEMBER, true),
                PolicyFixtures.pending(PolicyFixtures.membershipIn(pending, CommunityRole.COMMUNITY_MEMBER, true)))));
    }
}
