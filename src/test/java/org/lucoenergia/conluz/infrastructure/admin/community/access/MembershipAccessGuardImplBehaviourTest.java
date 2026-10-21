package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.MembershipAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Black-box characterization of {@link MembershipAccessGuardImpl}: real memberships on a real
 * {@link User} and the real helper, with only {@link AuthService} mocked.
 *
 * <p>The {@code canManageMemberships} tests mirror {@code MembershipAccessGuardImplTest} one to one.
 * {@code canManageMembershipInvestment} and {@code canReadMembershipPrivateData} had no tests at all
 * before this suite, despite detailed contract Javadoc on {@link MembershipAccessGuard}: anonymous
 * → {@code false} (401), every other denial → {@code CommunityNotFoundException} (404), never a
 * 403, and no platform-admin bypass on either.</p>
 */
@ExtendWith(MockitoExtension.class)
class MembershipAccessGuardImplBehaviourTest {

    @Mock
    private AuthService authService;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;

    private MembershipAccessGuard guard() {
        return new MembershipAccessGuardImpl(
                new CommunityAccessGuardHelper(authService, getCommunityRepository),
                getMembershipsRepository,
                new AccessPolicies().membership());
    }

    // --- canManageMemberships ---

    @Test
    @DisplayName("MEM-031 a platform admin who is not an admin of the community is refused its roster")
    void canManageMemberships_returnsFalse_whenUserIsPlatformAdmin() {
        // The community stays visible to a platform admin: 403, not 404.
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(guard().canManageMemberships(UUID.randomUUID()));
    }

    @Test
    void canManageMemberships_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_returnsFalse_whenUserIsCommunityMember() {
        // A member can see the community but is not an admin -> 403 (return false), not 404.
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_throwsNotFound_whenUserIsNotMember() {
        // A non-member cannot see the community -> 404 to avoid leaking its existence.
        Community community = CommunityMother.random().build();
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManageMemberships(UUID.randomUUID()));
    }

    @Test
    void canManageMemberships_throwsNotFound_whenAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_returnsFalse_whenPlatformAdminAndCommunityIdIsNull() {
        // A platform admin passes the visibility gate, but no community admin role matches a null id.
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(guard().canManageMemberships(null));
    }

    @Test
    void canManageMemberships_throwsNotFound_whenCommunityIdIsNullAndUserIsNotPlatformAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().canManageMemberships(null));
    }

    // --- canManageMembershipInvestment ---
    // No platform-admin bypass and no 403 branch: anyone who is not an enabled community admin here
    // is told the community is not there rather than that they may not touch it.

    @Test
    void canManageMembershipInvestment_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canManageMembershipInvestment(community.getId()));
    }

    @Test
    void canManageMembershipInvestment_throwsNotFound_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canManageMembershipInvestment(community.getId()));
    }

    @Test
    void canManageMembershipInvestment_throwsNotFound_whenUserIsPlatformAdminButNotCommunityAdmin() {
        // Deliberately no platform-admin bypass: an investment is the member's own money.
        Community community = CommunityMother.random().build();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canManageMembershipInvestment(community.getId()));
    }

    @Test
    void canManageMembershipInvestment_throwsNotFound_whenUserIsNotMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canManageMembershipInvestment(community.getId()));
    }

    @Test
    void canManageMembershipInvestment_throwsNotFound_whenAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canManageMembershipInvestment(community.getId()));
    }

    @Test
    void canManageMembershipInvestment_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManageMembershipInvestment(UUID.randomUUID()));
    }

    @Test
    void canManageMembershipInvestment_returnsFalse_whenCommunityIdIsNull() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(guard().canManageMembershipInvestment(null));
    }

    // --- canReadMembershipPrivateData ---
    // Either the caller is the named user AND holds an enabled membership in the community, or they
    // are an enabled community admin of it. No platform-admin bypass, no 403 branch.

    @Test
    void canReadMembershipPrivateData_returnsTrue_whenCallerReadsOwnPaybackAsEnabledMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canReadMembershipPrivateData(community.getId(), user.getId()));
    }

    @Test
    void canReadMembershipPrivateData_throwsNotFound_whenCallerReadsOwnPaybackButMembershipIsDisabled() {
        // A disabled membership already makes its community invisible to its holder everywhere else;
        // payback must not be the one endpoint where it does not.
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPrivateData(community.getId(), user.getId()));
    }

    @Test
    void canReadMembershipPrivateData_throwsNotFound_whenCallerReadsOwnPaybackInACommunityTheyDoNotBelongTo() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPrivateData(community.getId(), user.getId()));
    }

    @Test
    void canReadMembershipPrivateData_returnsTrue_whenCallerIsCommunityAdminOfTheCommunity() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canReadMembershipPrivateData(community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPrivateData_throwsNotFound_whenCallerIsAPlainMemberReadingAnotherUser() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPrivateData(community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPrivateData_throwsNotFound_whenCallerIsPlatformAdminButNotAMemberOrAdmin() {
        // A platform admin reaches their own payback through the self branch, like anyone else --
        // administering the platform does not confer another member's financial position.
        Community community = CommunityMother.random().build();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPrivateData(community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPrivateData_throwsNotFound_whenAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPrivateData(community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPrivateData_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadMembershipPrivateData(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPrivateData_returnsFalse_whenCommunityIdIsNull() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canReadMembershipPrivateData(null, user.getId()));
    }

    @Test
    void canReadMembershipPrivateData_returnsFalse_whenUserIdIsNull() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canReadMembershipPrivateData(community.getId(), null));
    }

    // --- canChangeMemberState ---

    @Test
    @DisplayName("MEM-027 an active community admin may change the state of a member's membership")
    void canChangeMemberState_returnsTrue_forAnActiveCommunityAdminOnAMembersMembership() {
        Community community = CommunityMother.random().build();
        User target = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser())
                .thenReturn(Optional.of(userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true)));
        when(getMembershipsRepository.findByUserIdAndCommunityId(target.getId(), community.getId()))
                .thenReturn(Optional.of(target.getMemberships().get(0)));

        assertTrue(guard().canChangeMemberState(community.getId(), target.getId()));
    }

    @Test
    @DisplayName("MEM-027 an admin's membership is not found")
    void canChangeMemberState_throwsMembershipNotFound_onAnAdminsMembership() {
        Community community = CommunityMother.random().build();
        User target = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser())
                .thenReturn(Optional.of(userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true)));
        when(getMembershipsRepository.findByUserIdAndCommunityId(target.getId(), community.getId()))
                .thenReturn(Optional.of(target.getMemberships().get(0)));

        assertThrows(MembershipNotFoundException.class,
                () -> guard().canChangeMemberState(community.getId(), target.getId()));
    }

    @Test
    @DisplayName("MEM-027 a user without a membership in the community is not found")
    void canChangeMemberState_throwsMembershipNotFound_withoutAMembership() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(authService.getCurrentUser())
                .thenReturn(Optional.of(userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true)));
        when(getMembershipsRepository.findByUserIdAndCommunityId(userId, community.getId()))
                .thenReturn(Optional.empty());

        assertThrows(MembershipNotFoundException.class,
                () -> guard().canChangeMemberState(community.getId(), userId));
    }

    @Test
    @DisplayName("MEM-027 a platform admin who is not an admin of the community is refused, without loading the membership")
    void canChangeMemberState_returnsFalse_forAPlatformAdmin() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(guard().canChangeMemberState(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(getMembershipsRepository);
    }

    @Test
    @DisplayName("MEM-027 a disabled admin cannot see the community")
    void canChangeMemberState_throwsCommunityNotFound_forADisabledAdmin() {
        Community community = CommunityMother.random().build();
        when(authService.getCurrentUser())
                .thenReturn(Optional.of(userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false)));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canChangeMemberState(community.getId(), UUID.randomUUID()));
        verifyNoInteractions(getMembershipsRepository);
    }

    @Test
    void canChangeMemberState_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canChangeMemberState(UUID.randomUUID(), UUID.randomUUID()));
    }

    // --- helpers ---

    private User userWithMembership(Community community, CommunityRole communityRole, boolean enabled) {
        User user = UserMother.randomUser();
        CommunityMembership membership = CommunityMembershipMother.active()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community)
                .withRole(communityRole)
                .withEnabled(enabled)
                .build();
        user.setMemberships(List.of(membership));
        return user;
    }
}
