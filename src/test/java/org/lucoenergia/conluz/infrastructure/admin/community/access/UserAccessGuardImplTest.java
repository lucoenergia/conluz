package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.UserAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessGuardImplTest {

    @Mock
    private CommunityAccessGuardHelper helper;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;

    private UserAccessGuard guard() {
        return new UserAccessGuardImpl(helper, getMembershipsRepository);
    }

    // --- canReadUser ---

    @Test
    void canReadUser_returnsTrue_whenUserIsPlatformAdmin() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canReadUser(UUID.randomUUID()));
    }

    @Test
    void canReadUser_returnsTrue_whenUserIsSelf() {
        User caller = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canReadUser(caller.getId()));
    }

    @Test
    void canReadUser_returnsTrue_whenCallerIsCommunityAdminOfTargetUserCommunity() {
        UUID sharedCommunityId = UUID.randomUUID();

        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        User caller = UserMother.randomUser();
        CommunityMembership adminMembership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(caller)
                .withCommunity(CommunityMother.random().withId(sharedCommunityId).build())
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(true).build();
        caller.setMemberships(List.of(adminMembership));
        when(helper.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canReadUser(targetUserId));
    }

    @Test
    void canReadUser_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadUser(UUID.randomUUID()));
    }

    @Test
    void canReadUser_throwsNotFound_whenTargetUserHasNoCommunities() {
        // The caller cannot see the target user -> 404 to avoid leaking the user's existence.
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId)).thenReturn(List.of());

        User caller = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(caller));

        assertThrows(UserNotFoundException.class, () -> guard().canReadUser(targetUserId));
    }

    // --- canEditUser ---

    @Test
    void canEditUser_returnsTrue_whenUserIsPlatformAdmin() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canEditUser(UUID.randomUUID()));
    }

    @Test
    void canEditUser_returnsTrue_whenCallerIsCommunityAdminOfTargetUserCommunity() {
        UUID sharedCommunityId = UUID.randomUUID();

        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        User caller = UserMother.randomUser();
        CommunityMembership adminMembership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(caller)
                .withCommunity(CommunityMother.random().withId(sharedCommunityId).build())
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(true).build();
        caller.setMemberships(List.of(adminMembership));
        when(helper.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canEditUser(targetUserId));
    }

    @Test
    void canEditUser_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canEditUser(UUID.randomUUID()));
    }

    @Test
    void canEditUser_throwsNotFound_whenCallerIsRegularPartner() {
        // A regular partner cannot see another user -> 404 to avoid leaking the user's existence.
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId)).thenReturn(List.of());

        User caller = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(caller));

        assertThrows(UserNotFoundException.class, () -> guard().canEditUser(targetUserId));
    }

    // --- canCreateUserIn ---

    @Test
    void canCreateUserIn_returnsTrue_whenUserIsPlatformAdmin() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canCreateUserIn(UUID.randomUUID()));
    }

    @Test
    void canCreateUserIn_throwsNotFound_whenNotPlatformAdminAndNotMember() {
        // A non-member cannot see the community -> 404 to avoid leaking its existence.
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(List.of());
        UUID communityId = UUID.randomUUID();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.canSeeCommunity(user, communityId)).thenReturn(false);

        assertThrows(CommunityNotFoundException.class, () -> guard().canCreateUserIn(communityId));
    }

    @Test
    void canCreateUserIn_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.canSeeCommunity(user, community.getId())).thenReturn(true);
        when(helper.hasCommunityAdminRoleIn(user, community.getId())).thenReturn(true);

        assertTrue(guard().canCreateUserIn(community.getId()));
    }

    @Test
    void canCreateUserIn_returnsFalse_whenUserIsCommunityMember() {
        // A member can see the community but is not an admin -> 403 (return false), not 404.
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.canSeeCommunity(user, community.getId())).thenReturn(true);
        when(helper.hasCommunityAdminRoleIn(user, community.getId())).thenReturn(false);

        assertFalse(guard().canCreateUserIn(community.getId()));
    }

    @Test
    void canCreateUserIn_returnsFalse_whenCommunityIdIsNull() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canCreateUserIn(null));
    }

    @Test
    void canCreateUserIn_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canCreateUserIn(UUID.randomUUID()));
    }

    // --- canListUsers ---

    @Test
    void canListUsers_returnsTrue_whenUserIsPlatformAdmin() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsTrue_whenUserHasCommunityAdminMembership() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(true).build();
        user.setMemberships(List.of(membership));
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsFalse_whenUserHasOnlyCommunityMemberMemberships() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(true).build();
        user.setMemberships(List.of(membership));
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsFalse_whenUserHasNoMemberships() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(null);
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsFalse_whenCommunityAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(false).build();
        user.setMemberships(List.of(membership));
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canListUsers());
    }

    // --- helpers ---

    private CommunityMembership membershipDomainInCommunity(UUID communityId, CommunityRole role, boolean enabled) {
        Community community = CommunityMother.random().withId(communityId).build();
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(community)
                .withRole(role)
                .withEnabled(enabled)
                .build();
    }
}
