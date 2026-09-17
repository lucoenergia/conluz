package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityAccessGuardImplTest {

    @Mock
    private AuthService authService;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;

    private CommunityAccessGuard guard() {
        return new CommunityAccessGuardImpl(authService, getCommunityRepository, getMembershipsRepository,
                getSupplyRepository, getPlantRepository, getSharingAgreementRepository);
    }

    // --- canReadCommunity ---

    @Test
    void canReadCommunity_returnsTrue_whenUserIsPlatformAdmin() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canReadCommunity(UUID.randomUUID()));
    }

    @Test
    void canReadCommunity_returnsTrue_whenUserIsMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canReadCommunity(community.getId()));
    }

    @Test
    void canReadCommunity_throwsNotFound_whenUserIsNotMember() {
        // A non-member cannot see the community -> 404 to avoid leaking its existence.
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().canReadCommunity(UUID.randomUUID()));
    }

    // --- isMemberOfCommunity ---

    @Test
    void isMemberOfCommunity_returnsFalse_whenNoAuthenticatedUser() {
        // No authenticated caller -> false, which the guard chain maps to a 401.
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().isMemberOfCommunity(UUID.randomUUID()));
    }

    @Test
    void isMemberOfCommunity_returnsTrue_whenUserIsEnabledMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().isMemberOfCommunity(community.getId()));
    }

    @Test
    void isMemberOfCommunity_returnsTrue_whenUserIsEnabledAdminMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().isMemberOfCommunity(community.getId()));
    }

    @Test
    void isMemberOfCommunity_returnsFalse_whenUserIsPlatformAdminButNotMember() {
        // A platform admin can see every community (so no 404) but is not a member -> false (403).
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        admin.setMemberships(List.of());
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(guard().isMemberOfCommunity(UUID.randomUUID()));
    }

    @Test
    void isMemberOfCommunity_throwsNotFound_whenUserIsNotMember() {
        // A non-member cannot see the community -> 404 to avoid leaking its existence.
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(List.of());
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().isMemberOfCommunity(UUID.randomUUID()));
    }

    @Test
    void isMemberOfCommunity_throwsNotFound_whenMembershipIsDisabled() {
        // A disabled membership does not let the user see the community -> 404.
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().isMemberOfCommunity(community.getId()));
    }

    // --- canReadCommunityProduction / canListSupplies ---
    //
    // Both are aliases of isMemberOfCommunity, named for the endpoints they guard. The cases below
    // are the same ones isMemberOfCommunity is pinned on, so an alias rewired to a different (or
    // inlined, and then drifting) rule fails here.

    private static Stream<Arguments> memberScopedMethods() {
        return Stream.of(
                Arguments.of("canReadCommunityProduction",
                        (BiFunction<CommunityAccessGuard, UUID, Boolean>)
                                CommunityAccessGuard::canReadCommunityProduction),
                Arguments.of("canListSupplies",
                        (BiFunction<CommunityAccessGuard, UUID, Boolean>)
                                CommunityAccessGuard::canListSupplies));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberScopedMethods")
    void memberScopedMethod_returnsFalse_whenNoAuthenticatedUser(
            String name, BiFunction<CommunityAccessGuard, UUID, Boolean> method) {
        // No authenticated caller -> false, which the guard chain maps to a 401.
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(method.apply(guard(), UUID.randomUUID()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberScopedMethods")
    void memberScopedMethod_returnsTrue_whenUserIsEnabledMember(
            String name, BiFunction<CommunityAccessGuard, UUID, Boolean> method) {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(method.apply(guard(), community.getId()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberScopedMethods")
    void memberScopedMethod_returnsTrue_whenUserIsEnabledAdminMember(
            String name, BiFunction<CommunityAccessGuard, UUID, Boolean> method) {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(method.apply(guard(), community.getId()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberScopedMethods")
    void memberScopedMethod_returnsFalse_whenUserIsPlatformAdminButNotMember(
            String name, BiFunction<CommunityAccessGuard, UUID, Boolean> method) {
        // A platform admin can see every community (so no 404) but is not a member -> false (403).
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        admin.setMemberships(List.of());
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(method.apply(guard(), UUID.randomUUID()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberScopedMethods")
    void memberScopedMethod_throwsNotFound_whenUserIsNotMember(
            String name, BiFunction<CommunityAccessGuard, UUID, Boolean> method) {
        // A non-member cannot see the community -> 404 to avoid leaking its existence.
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(List.of());
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        CommunityAccessGuard guard = guard();
        UUID communityId = UUID.randomUUID();
        assertThrows(CommunityNotFoundException.class, () -> method.apply(guard, communityId));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberScopedMethods")
    void memberScopedMethod_throwsNotFound_whenMembershipIsDisabled(
            String name, BiFunction<CommunityAccessGuard, UUID, Boolean> method) {
        // A disabled membership does not let the user see the community -> 404.
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        CommunityAccessGuard guard = guard();
        UUID communityId = community.getId();
        assertThrows(CommunityNotFoundException.class, () -> method.apply(guard, communityId));
    }

    // --- canManageCommunity ---

    @Test
    void canManageCommunity_returnsFalse_whenUserIsPlatformAdminButNotCommunityAdmin() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(guard().canManageCommunity(UUID.randomUUID()));
    }

    @Test
    void canManageCommunity_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canManageCommunity(community.getId()));
    }

    @Test
    void canManageCommunity_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canManageCommunity(community.getId()));
    }

    // --- platform privilege comes only from isPlatformAdmin + memberships ---

    @Test
    void userWithoutPlatformAdminOrMemberships_isDeniedEverything() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(List.of());
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        UUID communityId = UUID.randomUUID();
        // Cannot see the community -> 404 for both read and manage.
        assertThrows(CommunityNotFoundException.class, () -> guard().canReadCommunity(communityId));
        assertThrows(CommunityNotFoundException.class, () -> guard().canManageCommunity(communityId));
        assertTrue(guard().visibleCommunityIds().isEmpty());
    }

    // --- visibleCommunityIds ---

    @Test
    void visibleCommunityIds_returnsEmpty_whenNotPlatformAdminAndNoMemberships() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(List.of());
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().visibleCommunityIds().isEmpty());
    }

    @Test
    void visibleCommunityIds_returnsAllIds_whenUserIsPlatformAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getCommunityRepository.findAllIds()).thenReturn(Set.of(community.getId()));

        Set<UUID> result = guard().visibleCommunityIds();

        assertTrue(result.contains(community.getId()));
    }

    @Test
    void visibleCommunityIds_returnsEnabledCommunityIds_whenUserHasMemberships() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Set<UUID> result = guard().visibleCommunityIds();

        assertTrue(result.contains(community.getId()));
    }

    @Test
    void visibleCommunityIds_excludesDisabledMemberships() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().visibleCommunityIds().isEmpty());
    }

    @Test
    void visibleCommunityIds_returnsEmpty_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertTrue(guard().visibleCommunityIds().isEmpty());
    }

    // --- adminCommunityIds ---

    @Test
    void adminCommunityIds_returnsEmpty_whenUserIsPlatformAdminButDoesntAdminAnyCommunity() {
        Community community = CommunityMother.random().build();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        Set<UUID> result = guard().adminCommunityIds();

        assertFalse(result.contains(community.getId()));
    }

    @Test
    void adminCommunityIds_returnsOnlyCommunitiesWhereUserIsEnabledAdmin() {
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
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Set<UUID> result = guard().adminCommunityIds();

        assertTrue(result.contains(adminCommunity.getId()));
        assertFalse(result.contains(memberCommunity.getId()));
    }

    @Test
    void adminCommunityIds_excludesDisabledAdminMemberships() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().adminCommunityIds().isEmpty());
    }

    @Test
    void adminCommunityIds_returnsEmpty_whenNotPlatformAdminAndNoMemberships() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        user.setMemberships(List.of());
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().adminCommunityIds().isEmpty());
    }

    @Test
    void adminCommunityIds_returnsEmpty_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertTrue(guard().adminCommunityIds().isEmpty());
    }

    // --- isCurrentUser ---

    @Test
    void isCurrentUser_returnsTrue_whenIdMatchesAuthenticatedUser() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().isCurrentUser(user.getId()));
    }

    @Test
    void isCurrentUser_returnsFalse_whenIdDoesNotMatch() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().isCurrentUser(UUID.randomUUID()));
    }

    @Test
    void isCurrentUser_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().isCurrentUser(UUID.randomUUID()));
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
