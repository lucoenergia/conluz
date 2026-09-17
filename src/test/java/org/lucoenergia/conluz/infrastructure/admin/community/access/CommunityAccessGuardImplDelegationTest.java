package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
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
 * Pins that {@link CommunityAccessGuardImpl} forwards each of its delegated methods to the right
 * sub-guard. Nothing tested this before: {@code CommunityAccessGuardImplTest} only covers the
 * facade's own methods, so a method wired to the wrong sub-guard would have gone unnoticed.
 *
 * <p>Each test picks an arrangement whose outcome is distinctive of exactly one sub-guard — the
 * {@code *NotFoundException} type it throws, or the one branch that returns {@code true}. A
 * delegation pointing at the wrong sub-guard therefore fails loudly rather than coincidentally
 * agreeing.</p>
 */
@ExtendWith(MockitoExtension.class)
class CommunityAccessGuardImplDelegationTest {

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
                getSupplyRepository, getPlantRepository, getSharingAgreementRepository, new AccessPolicies());
    }

    // --- delegation to SupplyAccessGuard ---

    @Test
    void canReadSupply_isDelegatedToTheSupplyGuard() {
        UUID supplyId = givenMissingSupply();

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supplyId));
    }

    @Test
    void canReadSupply_isDelegatedToTheSupplyGuard_inTheAllowingDirection() {
        User user = authenticated(UserMother.randomUser());
        Supply supply = supplyOwnedBy(user.getId());
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canReadSupply(supply.getId()));
    }

    @Test
    void canEditSupply_isDelegatedToTheSupplyGuard() {
        UUID supplyId = givenMissingSupply();

        assertThrows(SupplyNotFoundException.class, () -> guard().canEditSupply(supplyId));
    }

    @Test
    void isCommunityAdminOfSupply_isDelegatedToTheSupplyGuard() {
        // The one supply method that never throws: a missing supply is simply false.
        UUID supplyId = givenMissingSupply();

        assertFalse(guard().isCommunityAdminOfSupply(supplyId));
    }

    // --- delegation to MembershipAccessGuard ---

    @Test
    void canManageMemberships_isDelegatedToTheMembershipGuard() {
        authenticated(UserMother.randomUser());

        assertThrows(CommunityNotFoundException.class, () -> guard().canManageMemberships(UUID.randomUUID()));
    }

    @Test
    void canManageMemberships_isDelegatedToTheMembershipGuard_inTheAllowingDirection() {
        Community community = CommunityMother.random().build();
        authenticated(userWithMembership(community, CommunityRole.COMMUNITY_ADMIN));

        assertTrue(guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMembershipInvestment_isDelegatedToTheMembershipGuard() {
        authenticated(UserMother.randomUser());

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canManageMembershipInvestment(UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPayback_isDelegatedToTheMembershipGuard() {
        authenticated(UserMother.randomUser());

        assertThrows(CommunityNotFoundException.class,
                () -> guard().canReadMembershipPayback(UUID.randomUUID(), UUID.randomUUID()));
    }

    // --- delegation to UserAccessGuard ---

    @Test
    void canReadUser_isDelegatedToTheUserGuard() {
        UUID targetUserId = givenTargetUserWithoutCommunities();

        assertThrows(UserNotFoundException.class, () -> guard().canReadUser(targetUserId));
    }

    @Test
    void canEditUser_isDelegatedToTheUserGuard() {
        UUID targetUserId = givenTargetUserWithoutCommunities();

        assertThrows(UserNotFoundException.class, () -> guard().canEditUser(targetUserId));
    }

    @Test
    void canCreateUserIn_isDelegatedToTheUserGuard() {
        authenticated(UserMother.randomUser());

        assertThrows(CommunityNotFoundException.class, () -> guard().canCreateUserIn(UUID.randomUUID()));
    }

    @Test
    void canListUsers_isDelegatedToTheUserGuard() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        authenticated(admin);

        assertTrue(guard().canListUsers());
    }

    // --- delegation to PlantAccessGuard ---

    @Test
    void canManagePlant_isDelegatedToThePlantGuard() {
        UUID plantId = givenMissingPlant();

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(plantId));
    }

    @Test
    void canReadPlant_isDelegatedToThePlantGuard() {
        UUID plantId = givenMissingPlant();

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plantId));
    }

    @Test
    void canCreatePlant_isDelegatedToThePlantGuard() {
        authenticated(UserMother.randomUser());
        when(getSupplyRepository.findByCode(SupplyCode.of("ES001"))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canCreatePlant("ES001"));
    }

    @Test
    void canListPlants_isDelegatedToThePlantGuard() {
        authenticated(UserMother.randomUser());

        assertThrows(CommunityNotFoundException.class, () -> guard().canListPlants(UUID.randomUUID()));
    }

    @Test
    void canListPlants_isDelegatedToThePlantGuard_inTheAllowingDirection() {
        Community community = CommunityMother.random().build();
        authenticated(userWithMembership(community, CommunityRole.COMMUNITY_MEMBER));

        assertTrue(guard().canListPlants(community.getId()));
    }

    @Test
    void canReadSharingAgreement_isDelegatedToThePlantGuard() {
        UUID plantId = givenMissingPlant();

        assertThrows(PlantNotFoundException.class,
                () -> guard().canReadSharingAgreement(plantId, UUID.randomUUID()));
    }

    @Test
    void canManageSharingAgreementForCreate_isDelegatedToThePlantGuard() {
        UUID plantId = givenMissingPlant();

        assertThrows(PlantNotFoundException.class, () -> guard().canManageSharingAgreement(plantId));
    }

    @Test
    void canManageSharingAgreementForWrite_isDelegatedToThePlantGuard() {
        UUID plantId = givenMissingPlant();

        assertThrows(PlantNotFoundException.class,
                () -> guard().canManageSharingAgreement(plantId, UUID.randomUUID()));
    }

    // --- helpers ---

    private User authenticated(User user) {
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        return user;
    }

    private User userWithMembership(Community community, CommunityRole role) {
        User user = UserMother.randomUser();
        user.setMemberships(List.of(new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(role).withEnabled(true).build()));
        return user;
    }

    private UUID givenMissingSupply() {
        authenticated(UserMother.randomUser());
        UUID supplyId = UUID.randomUUID();
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());
        return supplyId;
    }

    private UUID givenMissingPlant() {
        authenticated(UserMother.randomUser());
        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());
        return plantId;
    }

    private UUID givenTargetUserWithoutCommunities() {
        authenticated(UserMother.randomUser());
        UUID targetUserId = UUID.randomUUID();
        when(getMembershipsRepository.findByUserId(targetUserId)).thenReturn(List.of());
        return targetUserId;
    }

    private Supply supplyOwnedBy(UUID ownerId) {
        User owner = new User.Builder().id(ownerId).build();
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }
}
