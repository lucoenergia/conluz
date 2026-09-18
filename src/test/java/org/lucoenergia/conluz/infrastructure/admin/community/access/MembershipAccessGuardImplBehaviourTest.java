package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.MembershipAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
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
import static org.mockito.Mockito.when;

/**
 * Black-box characterization of {@link MembershipAccessGuardImpl}: real memberships on a real
 * {@link User} and the real helper, with only {@link AuthService} mocked.
 *
 * <p>The {@code canManageMemberships} tests mirror {@code MembershipAccessGuardImplTest} one to one.
 * {@code canManageMembershipInvestment} and {@code canReadMembershipPayback} had no tests at all
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

    private MembershipAccessGuard guard() {
        return new MembershipAccessGuardImpl(
                new CommunityAccessGuardHelper(authService, getCommunityRepository),
                new AccessPolicies().membership());
    }

    // --- canManageMemberships ---

    @Test
    void canManageMemberships_returnsTrue_whenUserIsPlatformAdmin() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canManageMemberships(UUID.randomUUID()));
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
    void canManageMemberships_returnsTrue_whenPlatformAdminAndCommunityIdIsNull() {
        // Characterizes today's behaviour, surprising as it is: this is the only membership method
        // without a null guard, and the platform-admin bypass sits after the visibility gate (which
        // a platform admin always passes), so a null community id is granted rather than rejected.
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canManageMemberships(null));
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

    // --- canReadMembershipPayback ---
    // Either the caller is the named user AND holds an enabled membership in the community, or they
    // are an enabled community admin of it. No platform-admin bypass, no 403 branch.

    @Test
    void canReadMembershipPayback_returnsTrue_whenCallerReadsOwnPaybackAsEnabledMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canReadMembershipPayback(community.getId(), user.getId()));
    }

    @Test
    void canReadMembershipPayback_throwsNotFound_whenCallerReadsOwnPaybackButMembershipIsDisabled() {
        // A disabled membership already makes its community invisible to its holder everywhere else;
        // payback must not be the one endpoint where it does not.
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPayback(community.getId(), user.getId()));
    }

    @Test
    void canReadMembershipPayback_throwsNotFound_whenCallerReadsOwnPaybackInACommunityTheyDoNotBelongTo() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPayback(community.getId(), user.getId()));
    }

    @Test
    void canReadMembershipPayback_returnsTrue_whenCallerIsCommunityAdminOfTheCommunity() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canReadMembershipPayback(community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPayback_throwsNotFound_whenCallerIsAPlainMemberReadingAnotherUser() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPayback(community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPayback_throwsNotFound_whenCallerIsPlatformAdminButNotAMemberOrAdmin() {
        // A platform admin reaches their own payback through the self branch, like anyone else --
        // administering the platform does not confer another member's financial position.
        Community community = CommunityMother.random().build();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPayback(community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPayback_throwsNotFound_whenAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPayback(community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPayback_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadMembershipPayback(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPayback_returnsFalse_whenCommunityIdIsNull() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canReadMembershipPayback(null, user.getId()));
    }

    @Test
    void canReadMembershipPayback_returnsFalse_whenUserIdIsNull() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canReadMembershipPayback(community.getId(), null));
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
}
