package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityAccessGuardHelperTest {

    @Mock
    private AuthService authService;
    @Mock
    private GetCommunityRepository getCommunityRepository;

    private CommunityAccessGuardHelper helper() {
        return new CommunityAccessGuardHelper(authService, getCommunityRepository);
    }

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenCommunityIdIsNull() {
        User user = UserMother.randomUser();
        assertFalse(helper().hasCommunityAdminRoleIn(user, null));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertFalse(helper().hasCommunityAdminRoleIn(user, UUID.randomUUID()));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsTrue_whenUserHasEnabledAdminRole() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(true).build();
        user.setMemberships(List.of(membership));

        assertTrue(helper().hasCommunityAdminRoleIn(user, community.getId()));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenRoleIsMemberNotAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(true).build();
        user.setMemberships(List.of(membership));

        assertFalse(helper().hasCommunityAdminRoleIn(user, community.getId()));
    }

    @Test
    void hasCommunityAdminRoleIn_returnsFalse_whenMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(false).build();
        user.setMemberships(List.of(membership));

        assertFalse(helper().hasCommunityAdminRoleIn(user, community.getId()));
    }

    @Test
    void hasMembershipInCommunity_returnsFalse_whenCommunityIdIsNull() {
        User user = UserMother.randomUser();
        assertFalse(helper().hasMembershipInCommunity(user, null));
    }

    @Test
    void hasMembershipInCommunity_returnsTrue_whenUserHasEnabledMembership() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(true).build();
        user.setMemberships(List.of(membership));

        assertTrue(helper().hasMembershipInCommunity(user, community.getId()));
    }

    @Test
    void hasMembershipInCommunity_returnsFalse_whenMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(false).build();
        user.setMemberships(List.of(membership));

        assertFalse(helper().hasMembershipInCommunity(user, community.getId()));
    }

    @Test
    void isCurrentUser_returnsTrue_whenIdsMatch() {
        User user = UserMother.randomUser();
        assertTrue(helper().isCurrentUser(user, user.getId()));
    }

    @Test
    void isCurrentUser_returnsFalse_whenIdsDontMatch() {
        User user = UserMother.randomUser();
        assertFalse(helper().isCurrentUser(user, UUID.randomUUID()));
    }

    @Test
    void isCurrentUser_returnsFalse_whenUserIsNull() {
        assertFalse(helper().isCurrentUser(null, UUID.randomUUID()));
    }

    @Test
    void isCurrentUser_returnsFalse_whenUserIdIsNull() {
        User user = UserMother.randomUser();
        assertFalse(helper().isCurrentUser(user, null));
    }

    @Test
    void visibleCommunityIds_returnsEmpty_whenUserIsNull() {
        assertTrue(helper().visibleCommunityIds(null).isEmpty());
    }

    @Test
    void visibleCommunityIds_returnsAllIds_whenUserIsPlatformAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        when(getCommunityRepository.findAllIds()).thenReturn(Set.of(community.getId()));

        Set<UUID> result = helper().visibleCommunityIds(user);
        assertTrue(result.contains(community.getId()));
    }

    @Test
    void visibleCommunityIds_returnsEmpty_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertTrue(helper().visibleCommunityIds(user).isEmpty());
    }

    @Test
    void visibleCommunityIds_returnsEnabledCommunityIds() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(true).build();
        user.setMemberships(List.of(membership));

        Set<UUID> result = helper().visibleCommunityIds(user);
        assertTrue(result.contains(community.getId()));
    }

    @Test
    void adminCommunityIds_returnsAllIds_whenUserIsPlatformAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        when(getCommunityRepository.findAllIds()).thenReturn(Set.of(community.getId()));

        Set<UUID> result = helper().adminCommunityIds(user);
        assertTrue(result.contains(community.getId()));
    }

    @Test
    void adminCommunityIds_returnsOnlyAdminCommunities() {
        Community adminCommunity = CommunityMother.random().build();
        Community memberCommunity = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership adminMembership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(adminCommunity)
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(true).build();
        CommunityMembership memberMembership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(memberCommunity)
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(true).build();
        user.setMemberships(List.of(adminMembership, memberMembership));

        Set<UUID> result = helper().adminCommunityIds(user);
        assertTrue(result.contains(adminCommunity.getId()));
        assertFalse(result.contains(memberCommunity.getId()));
    }
}
