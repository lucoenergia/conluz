package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityAccessGuardTest {

    @Mock
    private AuthService authService;

    private CommunityAccessGuard guard() {
        return new CommunityAccessGuardImpl(authService);
    }

    // --- canReadSupply ---

    @Test
    void canReadSupply_returnsTrue_whenUserIsAdmin() {
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        Supply supply = supplyOwnedBy(UUID.randomUUID());

        assertTrue(guard().canReadSupply(supply));
    }

    @Test
    void canReadSupply_returnsTrue_whenUserIsOwner() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyOwnedBy(user.getId());

        assertTrue(guard().canReadSupply(supply));
    }

    @Test
    void canReadSupply_returnsTrue_whenUserIsMemberOfSupplyCommunity() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);

        assertTrue(guard().canReadSupply(supply));
    }

    @Test
    void canReadSupply_returnsFalse_whenUserIsNotMemberOfSupplyCommunity() {
        Community communityA = CommunityMother.random().build();
        Community communityB = CommunityMother.random().build();
        User user = userWithMembership(communityA, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), communityB);

        assertFalse(guard().canReadSupply(supply));
    }

    @Test
    void canReadSupply_returnsFalse_whenMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);

        assertFalse(guard().canReadSupply(supply));
    }

    @Test
    void canReadSupply_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadSupply(supplyOwnedBy(UUID.randomUUID())));
    }

    // --- canEditSupply ---

    @Test
    void canEditSupply_returnsTrue_whenUserIsAdmin() {
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canEditSupply(supplyOwnedBy(UUID.randomUUID())));
    }

    @Test
    void canEditSupply_returnsTrue_whenUserIsOwner() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canEditSupply(supplyOwnedBy(user.getId())));
    }

    @Test
    void canEditSupply_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);

        assertTrue(guard().canEditSupply(supply));
    }

    @Test
    void canEditSupply_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);

        assertFalse(guard().canEditSupply(supply));
    }

    // --- canReadCommunity ---

    @Test
    void canReadCommunity_returnsTrue_whenUserIsAdmin() {
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
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
    void canReadCommunity_returnsFalse_whenUserIsNotMember() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canReadCommunity(UUID.randomUUID()));
    }

    // --- canManageCommunity ---

    @Test
    void canManageCommunity_returnsTrue_whenUserIsAdmin() {
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canManageCommunity(UUID.randomUUID()));
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

    // --- canManagePlatform ---

    @Test
    void canManagePlatform_returnsTrue_whenUserIsPlatformAdmin() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canManagePlatform());
    }

    @Test
    void canManagePlatform_returnsFalse_whenUserIsNotPlatformAdmin() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canManagePlatform());
    }

    @Test
    void canManagePlatform_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManagePlatform());
    }

    // --- helpers ---

    private Supply supplyOwnedBy(UUID ownerId) {
        User owner = new User.Builder().id(ownerId).build();
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withName("Supply")
                .withAddress("Address")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();
    }

    private Supply supplyInCommunity(UUID ownerId, Community community) {
        User owner = new User.Builder().id(ownerId).build();
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withCommunity(community)
                .withName("Supply")
                .withAddress("Address")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();
    }

    private User userWithMembership(Community community, CommunityRole communityRole, boolean enabled) {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
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
