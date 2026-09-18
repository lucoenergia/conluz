package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.PlantAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
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
 * Black-box characterization of {@link PlantAccessGuardImpl}: real memberships on a real
 * {@link User} and the real helper, with only {@link AuthService} and the repositories mocked.
 *
 * <p>Mirrors {@code PlantAccessGuardImplTest} one to one, plus the null-argument,
 * disabled-membership and missing-agreement cases it never reached. Plant access has no
 * platform-admin bypass at all: visibility runs through {@code hasMembershipInCommunity}, so a
 * non-member platform admin gets a 404.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlantAccessGuardImplBehaviourTest {

    @Mock
    private AuthService authService;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;

    private PlantAccessGuard guard() {
        AccessPolicies policies = new AccessPolicies();
        return new PlantAccessGuardImpl(new CommunityAccessGuardHelper(authService, getCommunityRepository),
                getPlantRepository, getSupplyRepository, getSharingAgreementRepository,
                policies.plant(), policies.sharingAgreement());
    }

    // --- canManagePlant ---

    @Test
    void canManagePlant_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManagePlant(UUID.randomUUID()));
    }

    @Test
    void canManagePlant_throwsNotFound_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(plantId));
    }

    @Test
    void canManagePlant_throwsNotFound_whenUserIsNotMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertTrue(guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_returnsFalse_whenUserIsCommunityMember() {
        // A member can see the plant but only admins may manage it -> 403 (return false), not 404.
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertFalse(guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_throwsNotFound_whenPlantSupplyHasNoCommunity() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        Plant plant = plantOf(supply);
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_throwsNotFound_whenAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_throwsNotFound_whenPlantIdIsNull() {
        // PlantId.of(null) is legal, so a null id becomes a lookup miss -> 404, not false.
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(null))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(null));
    }

    // --- canReadPlant ---

    @Test
    void canReadPlant_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadPlant(UUID.randomUUID()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenUserIsPlatformAdminAndDoesntBelongToCommunity() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plant.getId()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plantId));
    }

    @Test
    void canReadPlant_returnsTrue_whenUserIsMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertTrue(guard().canReadPlant(plant.getId()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenUserIsNotMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plant.getId()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenPlantSupplyHasNoCommunity() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        Plant plant = plantOf(supply);
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plant.getId()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plant.getId()));
    }

    // --- canReadSharingAgreement ---

    @Test
    void canReadSharingAgreement_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadSharingAgreement(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void canReadSharingAgreement_throwsNotFound_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class,
                () -> guard().canReadSharingAgreement(plantId, UUID.randomUUID()));
    }

    @Test
    void canReadSharingAgreement_throwsNotFound_whenUserIsNotMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class,
                () -> guard().canReadSharingAgreement(plant.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadSharingAgreement_throwsNotFound_whenAgreementDoesNotExist() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(agreementId)).thenReturn(Optional.empty());

        assertThrows(SharingAgreementNotFoundException.class,
                () -> guard().canReadSharingAgreement(plant.getId(), agreementId));
    }

    @Test
    void canReadSharingAgreement_throwsNotFound_whenAgreementBelongsToAnotherPlant() {
        // The agreement exists but under a different plant -> the caller must not be able to tell
        // it exists elsewhere -> 404, same as a non-existent agreement.
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(agreementId))
                .thenReturn(Optional.of(agreementOf(agreementId, UUID.randomUUID())));

        assertThrows(SharingAgreementNotFoundException.class,
                () -> guard().canReadSharingAgreement(plant.getId(), agreementId));
    }

    @Test
    void canReadSharingAgreement_returnsTrue_whenUserIsCommunityAdminAndAgreementBelongsToPlant() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(agreementId))
                .thenReturn(Optional.of(agreementOf(agreementId, plant.getId())));

        assertTrue(guard().canReadSharingAgreement(plant.getId(), agreementId));
    }

    @Test
    void canReadSharingAgreement_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(agreementId))
                .thenReturn(Optional.of(agreementOf(agreementId, plant.getId())));

        assertFalse(guard().canReadSharingAgreement(plant.getId(), agreementId));
    }

    // --- canManageSharingAgreement(plantId) ---

    @Test
    void canManageSharingAgreementForCreate_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManageSharingAgreement(UUID.randomUUID()));
    }

    @Test
    void canManageSharingAgreementForCreate_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertTrue(guard().canManageSharingAgreement(plant.getId()));
    }

    @Test
    void canManageSharingAgreementForCreate_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertFalse(guard().canManageSharingAgreement(plant.getId()));
    }

    @Test
    void canManageSharingAgreementForCreate_throwsNotFound_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> guard().canManageSharingAgreement(plantId));
    }

    // --- canManageSharingAgreement(plantId, sharingAgreementId) ---

    @Test
    void canManageSharingAgreementForWrite_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManageSharingAgreement(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void canManageSharingAgreementForWrite_throwsNotFound_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class,
                () -> guard().canManageSharingAgreement(plantId, UUID.randomUUID()));
    }

    @Test
    void canManageSharingAgreementForWrite_throwsNotFound_whenAgreementBelongsToAnotherPlant() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(agreementId))
                .thenReturn(Optional.of(agreementOf(agreementId, UUID.randomUUID())));

        assertThrows(SharingAgreementNotFoundException.class,
                () -> guard().canManageSharingAgreement(plant.getId(), agreementId));
    }

    @Test
    void canManageSharingAgreementForWrite_throwsNotFound_whenAgreementDoesNotExist() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(agreementId)).thenReturn(Optional.empty());

        assertThrows(SharingAgreementNotFoundException.class,
                () -> guard().canManageSharingAgreement(plant.getId(), agreementId));
    }

    @Test
    void canManageSharingAgreementForWrite_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(agreementId))
                .thenReturn(Optional.of(agreementOf(agreementId, plant.getId())));

        assertTrue(guard().canManageSharingAgreement(plant.getId(), agreementId));
    }

    @Test
    void canManageSharingAgreementForWrite_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = plantOf(supply);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(agreementId))
                .thenReturn(Optional.of(agreementOf(agreementId, plant.getId())));

        assertFalse(guard().canManageSharingAgreement(plant.getId(), agreementId));
    }

    // --- canListPlants ---

    @Test
    void canListPlants_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canListPlants(UUID.randomUUID()));
    }

    @Test
    void canListPlants_returnsFalse_whenUserIsPlatformAdminAndDoesntBelongToCommunity() {
        // A platform admin can see the community, so this is a 403 rather than a 404.
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(guard().canListPlants(UUID.randomUUID()));
    }

    @Test
    void canListPlants_returnsTrue_whenUserIsMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertTrue(guard().canListPlants(community.getId()));
    }

    @Test
    void canListPlants_throwsNotFound_whenUserIsNotMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().canListPlants(community.getId()));
    }

    @Test
    void canListPlants_throwsNotFound_whenMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(CommunityNotFoundException.class, () -> guard().canListPlants(community.getId()));
    }

    @Test
    void canListPlants_returnsFalse_whenPlatformAdminAndCommunityIdIsNull() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        assertFalse(guard().canListPlants(null));
    }

    // --- canCreatePlant ---

    @Test
    void canCreatePlant_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canCreatePlant("ES001"));
    }

    @Test
    void canCreatePlant_returnsFalse_whenSupplyCodeIsNull() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canCreatePlant(null));
    }

    @Test
    void canCreatePlant_throwsNotFound_whenSupplyNotFoundByCode() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canCreatePlant(supplyCode));
    }

    @Test
    void canCreatePlant_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertTrue(guard().canCreatePlant(supplyCode));
    }

    @Test
    void canCreatePlant_returnsFalse_whenUserOwnsSupplyButIsNotCommunityAdmin() {
        // The owner can see the supply but only community admins may create plants -> 403.
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        Supply supply = supplyInCommunityOwnedBy(user, community);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertFalse(guard().canCreatePlant(supplyCode));
    }

    @Test
    void canCreatePlant_throwsNotFound_whenUserCannotSeeSupply() {
        // Neither the supply's owner nor an admin of its community -> cannot see it -> 404.
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canCreatePlant(supplyCode));
    }

    @Test
    void canCreatePlant_throwsNotFound_whenSupplyHasNoCommunity() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canCreatePlant(supplyCode));
    }

    @Test
    void canCreatePlant_throwsNotFound_whenAdminMembershipIsDisabled() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canCreatePlant(supplyCode));
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

    private Plant plantOf(Supply supply) {
        return new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
    }

    private SharingAgreement agreementOf(UUID agreementId, UUID plantId) {
        return new SharingAgreement.Builder()
                .withId(agreementId)
                .withPlantId(plantId)
                .build();
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

    private Supply supplyInCommunity(UUID ownerId, Community community) {
        User owner = new User.Builder().id(ownerId).build();
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withCommunity(community)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }

    private Supply supplyInCommunityOwnedBy(User owner, Community community) {
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withCommunity(community)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }
}
