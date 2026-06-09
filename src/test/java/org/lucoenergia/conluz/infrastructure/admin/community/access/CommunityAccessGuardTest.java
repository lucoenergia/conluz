package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityAccessGuardTest {

    @Mock
    private AuthService authService;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;

    private CommunityAccessGuard guard() {
        return new CommunityAccessGuardImpl(authService, getMembershipsRepository,
                getSupplyRepository, getPlantRepository, getSharingAgreementRepository);
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
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_returnsTrue_whenUserIsOwner() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        Supply supply = supplyOwnedBy(user.getId());
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertFalse(guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_throwsNotFoundException_whenAdminAndSupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canEditSupply(supplyId));
    }

    @Test
    void canEditSupply_returnsFalse_whenNonAdminAndSupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertFalse(guard().canEditSupply(supplyId));
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

    // --- canManageMemberships ---

    @Test
    void canManageMemberships_returnsTrue_whenUserIsAdmin() {
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
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
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManageMemberships(UUID.randomUUID()));
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
        Community sharedCommunity = CommunityMother.random().withId(sharedCommunityId).build();

        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        User caller = userWithMembership(sharedCommunity, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canReadUser(targetUserId));
    }

    @Test
    void canReadUser_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadUser(UUID.randomUUID()));
    }

    @Test
    void canReadUser_returnsFalse_whenTargetUserHasNoCommunities() {
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId)).thenReturn(List.of());

        User caller = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertFalse(guard().canReadUser(targetUserId));
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
        Community sharedCommunity = CommunityMother.random().withId(sharedCommunityId).build();

        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId))
                .thenReturn(List.of(membershipDomainInCommunity(sharedCommunityId, CommunityRole.COMMUNITY_MEMBER, true)));

        User caller = userWithMembership(sharedCommunity, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canEditUser(targetUserId));
    }

    @Test
    void canEditUser_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canEditUser(UUID.randomUUID()));
    }

    @Test
    void canEditUser_returnsFalse_whenCallerIsRegularPartner() {
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId)).thenReturn(List.of());

        User caller = UserMother.randomUser();
        caller.setRole(Role.PARTNER);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertFalse(guard().canEditUser(targetUserId));
    }

    // --- visibleCommunityIds ---

    @Test
    void visibleCommunityIds_returnsNull_whenUserIsAdmin() {
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertNull(guard().visibleCommunityIds());
    }

    @Test
    void visibleCommunityIds_returnsNull_whenUserIsPlatformAdmin() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertNull(guard().visibleCommunityIds());
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

    // --- canCreateUserIn ---

    @Test
    void canCreateUserIn_returnsTrue_whenUserIsPlatformAdmin() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));

        assertTrue(guard().canCreateUserIn(UUID.randomUUID()));
    }

    @Test
    void canCreateUserIn_returnsTrue_whenUserIsAdmin() {
        User admin = UserMother.randomUser();
        admin.setRole(Role.ADMIN);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canCreateUserIn(UUID.randomUUID()));
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
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canCreateUserIn(community.getId()));
    }

    @Test
    void canCreateUserIn_returnsFalse_whenCommunityIdIsNull() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canCreateUserIn(null));
    }

    @Test
    void canCreateUserIn_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canCreateUserIn(UUID.randomUUID()));
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
