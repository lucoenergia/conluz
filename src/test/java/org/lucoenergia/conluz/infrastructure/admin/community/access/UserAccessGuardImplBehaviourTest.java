package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.UserAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Black-box characterization of {@link UserAccessGuardImpl}: real memberships on a real
 * {@link User} and the real helper, with only {@link AuthService} and
 * {@link GetMembershipsRepository} mocked.
 *
 * <p>Mirrors {@code UserAccessGuardImplTest} one to one, plus the disabled-membership and
 * self-edit cases it never reached. Note that the platform-admin and self branches deliberately
 * do not stub {@code findByUserId}: today they short-circuit before the repository is consulted,
 * and pinning that here keeps a future refactor from quietly adding a query per call.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserAccessGuardImplBehaviourTest {

    @Mock
    private AuthService authService;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;

    private UserAccessGuard guard() {
        return new UserAccessGuardImpl(new CommunityAccessGuardHelper(authService, getCommunityRepository),
                getMembershipsRepository);
    }

    // --- canReadUser ---

    @Test
    void canReadUser_returnsTrue_whenUserIsPlatformAdmin() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canReadUser(UUID.randomUUID()));
    }

    @Test
    void canReadUser_returnsTrue_whenUserIsSelf() {
        User caller = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canReadUser(caller.getId()));
    }

    @Test
    void canReadUser_returnsTrue_whenCallerIsCommunityAdminOfTargetUserCommunity() {
        UUID sharedCommunityId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        User caller = callerAdminOf(sharedCommunityId, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canReadUser(targetUserId));
    }

    @Test
    void canReadUser_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadUser(UUID.randomUUID()));
    }

    @Test
    void canReadUser_throwsNotFound_whenTargetUserHasNoCommunities() {
        // The caller cannot see the target user -> 404 to avoid leaking the user's existence.
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId)).thenReturn(List.of());

        User caller = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertThrows(UserNotFoundException.class, () -> guard().canReadUser(targetUserId));
    }

    @Test
    void canReadUser_throwsNotFound_whenTargetUserOnlyHasDisabledMemberships() {
        UUID sharedCommunityId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, false)));

        User caller = callerAdminOf(sharedCommunityId, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertThrows(UserNotFoundException.class, () -> guard().canReadUser(targetUserId));
    }

    @Test
    void canReadUser_throwsNotFound_whenCallerAdminMembershipIsDisabled() {
        UUID sharedCommunityId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        User caller = callerAdminOf(sharedCommunityId, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertThrows(UserNotFoundException.class, () -> guard().canReadUser(targetUserId));
    }

    @Test
    void canReadUser_throwsNotFound_whenCallerIsOnlyAMemberOfTheTargetUserCommunity() {
        UUID sharedCommunityId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        User caller = UserMother.randomUser();
        caller.setMemberships(List.of(new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(caller)
                .withCommunity(CommunityMother.random().withId(sharedCommunityId).build())
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(true).build()));
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertThrows(UserNotFoundException.class, () -> guard().canReadUser(targetUserId));
    }

    // --- canEditUser ---

    @Test
    void canEditUser_returnsTrue_whenUserIsPlatformAdmin() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canEditUser(UUID.randomUUID()));
    }

    @Test
    void canEditUser_returnsTrue_whenCallerIsCommunityAdminOfTargetUserCommunity() {
        UUID sharedCommunityId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        User caller = callerAdminOf(sharedCommunityId, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canEditUser(targetUserId));
    }

    @Test
    void canEditUser_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canEditUser(UUID.randomUUID()));
    }

    @Test
    void canEditUser_throwsNotFound_whenCallerIsRegularPartner() {
        // A regular partner cannot see another user -> 404 to avoid leaking the user's existence.
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId)).thenReturn(List.of());

        User caller = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertThrows(UserNotFoundException.class, () -> guard().canEditUser(targetUserId));
    }

    @Test
    void canEditUser_returnsFalse_whenCallerIsAPlainMemberEditingThemselves() {
        // Characterizes today's behaviour: the visibility gate passes via the self branch, but the
        // permission check that follows only grants platform admins and community admins of one of
        // the target's communities -- so a plain member editing their own record gets a 403.
        UUID ownCommunityId = UUID.randomUUID();
        User caller = UserMother.randomUser();
        caller.setMemberships(List.of(new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(caller)
                .withCommunity(CommunityMother.random().withId(ownCommunityId).build())
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(true).build()));
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));
        when(getMembershipsRepository.findByUserId(caller.getId()))
                .thenReturn(List.of(membershipDomainInCommunity(ownCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        assertFalse(guard().canEditUser(caller.getId()));
    }

    @Test
    void canEditUser_returnsTrue_whenCallerIsCommunityAdminEditingThemselves() {
        UUID ownCommunityId = UUID.randomUUID();
        User caller = callerAdminOf(ownCommunityId, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));
        when(getMembershipsRepository.findByUserId(caller.getId()))
                .thenReturn(List.of(membershipDomainInCommunity(ownCommunityId, CommunityRole.COMMUNITY_ADMIN, true)));

        assertTrue(guard().canEditUser(caller.getId()));
    }

    // --- canCreateUserIn ---

    @Test
    void canCreateUserIn_returnsTrue_whenUserIsPlatformAdmin() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canCreateUserIn(UUID.randomUUID()));
    }

    @Test
    void canCreateUserIn_throwsNotFound_whenNotPlatformAdminAndNotMember() {
        // A non-member cannot see the community -> 404 to avoid leaking its existence.
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(List.of());
        UUID communityId = UUID.randomUUID();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().canCreateUserIn(communityId));
    }

    @Test
    void canCreateUserIn_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canCreateUserIn(community.getId()));
    }

    @Test
    void canCreateUserIn_returnsFalse_whenUserIsCommunityMember() {
        // A member can see the community but is not an admin -> 403 (return false), not 404.
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canCreateUserIn(community.getId()));
    }

    @Test
    void canCreateUserIn_returnsFalse_whenCommunityIdIsNull() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canCreateUserIn(null));
    }

    @Test
    void canCreateUserIn_returnsTrue_whenPlatformAdminAndCommunityIdIsNull() {
        // The platform-admin bypass precedes the null check, so a null community id is granted.
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canCreateUserIn(null));
    }

    @Test
    void canCreateUserIn_throwsNotFound_whenAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().canCreateUserIn(community.getId()));
    }

    @Test
    void canCreateUserIn_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canCreateUserIn(UUID.randomUUID()));
    }

    // --- canListUsers ---

    @Test
    void canListUsers_returnsTrue_whenUserIsPlatformAdmin() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsTrue_whenUserHasCommunityAdminMembership() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsFalse_whenUserHasOnlyCommunityMemberMemberships() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsFalse_whenUserHasNoMemberships() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(null);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsFalse_whenCommunityAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canListUsers());
    }

    @Test
    void canListUsers_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canListUsers());
    }

    // --- helpers ---

    private User userWithMembership(Community community, CommunityRole communityRole, boolean enabled) {
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community)
                .withRole(communityRole)
                .withEnabled(enabled)
                .build();
        user.setMemberships(List.of(membership));
        return user;
    }

    private User callerAdminOf(UUID communityId, boolean enabled) {
        User caller = UserMother.randomUser();
        caller.setMemberships(List.of(new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(caller)
                .withCommunity(CommunityMother.random().withId(communityId).build())
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(enabled).build()));
        return caller;
    }

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
