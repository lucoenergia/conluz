package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.PlantAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantAccessGuardImplTest {

    @Mock
    private CommunityAccessGuardHelper helper;
    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;

    private PlantAccessGuard guard() {
        return new PlantAccessGuardImpl(helper, getPlantRepository, getSupplyRepository, getSharingAgreementRepository);
    }

    // --- canManagePlant ---

    @Test
    void canManagePlant_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManagePlant(UUID.randomUUID()));
    }

    @Test
    void canManagePlant_throwsNotFound_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(plantId));
    }

    @Test
    void canManagePlant_throwsNotFound_whenUserIsNotMemberOfPlantCommunity() {
        // A platform admin who is not a member of the plant's community cannot see the plant -> 404.
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));

        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));
        when(helper.hasMembershipInCommunity(user, communityId)).thenReturn(true);
        when(helper.hasCommunityAdminRoleIn(user, communityId)).thenReturn(true);

        assertTrue(guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_returnsFalse_whenUserIsCommunityMember() {
        // A member can see the plant but only admins may manage it -> 403 (return false), not 404.
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));
        when(helper.hasMembershipInCommunity(user, communityId)).thenReturn(true);

        assertFalse(guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_throwsNotFound_whenPlantSupplyHasNoCommunity() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canManagePlant(plant.getId()));
    }

    // --- canReadPlant ---

    @Test
    void canReadPlant_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadPlant(UUID.randomUUID()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenUserIsPlatformAdminAndDoesntBelongToCommunity() {
        // Plant reads are community-scoped: a platform admin who is not a member of the plant's
        // community cannot see it -> 404.
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));

        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plant.getId()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plantId));
    }

    @Test
    void canReadPlant_returnsTrue_whenUserIsMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasMembershipInCommunity(user, communityId)).thenReturn(true);
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertTrue(guard().canReadPlant(plant.getId()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenUserIsNotMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plant.getId()));
    }

    @Test
    void canReadPlant_throwsNotFound_whenPlantSupplyHasNoCommunity() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class, () -> guard().canReadPlant(plant.getId()));
    }

    // --- canReadSharingAgreement ---

    @Test
    void canReadSharingAgreement_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadSharingAgreement(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void canReadSharingAgreement_throwsNotFound_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class,
                () -> guard().canReadSharingAgreement(plantId, UUID.randomUUID()));
    }

    @Test
    void canReadSharingAgreement_throwsNotFound_whenUserIsNotMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertThrows(PlantNotFoundException.class,
                () -> guard().canReadSharingAgreement(plant.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadSharingAgreement_throwsNotFound_whenAgreementDoesNotExist() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasMembershipInCommunity(user, communityId)).thenReturn(true);
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
        UUID communityId = community.getId();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasMembershipInCommunity(user, communityId)).thenReturn(true);
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(agreementId)
                .withPlantId(UUID.randomUUID())
                .build();
        when(getSharingAgreementRepository.findById(agreementId)).thenReturn(Optional.of(agreement));

        assertThrows(SharingAgreementNotFoundException.class,
                () -> guard().canReadSharingAgreement(plant.getId(), agreementId));
    }

    @Test
    void canReadSharingAgreement_returnsTrue_whenUserIsMemberAndAgreementBelongsToPlant() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder().withId(UUID.randomUUID()).withSupply(supply).build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasMembershipInCommunity(user, communityId)).thenReturn(true);
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(agreementId)
                .withPlantId(plant.getId())
                .build();
        when(getSharingAgreementRepository.findById(agreementId)).thenReturn(Optional.of(agreement));

        assertTrue(guard().canReadSharingAgreement(plant.getId(), agreementId));
    }

    // --- canListPlants ---

    @Test
    void canListPlants_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canListPlants(UUID.randomUUID()));
    }

    @Test
    void canListPlants_returnsFalse_whenUserIsPlatformAdminAndDoesntBelongToCommunity() {
        // A platform admin can see the community but is not a member, so listing its plants is
        // denied with a 403 (return false), not a 404.
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        UUID communityId = UUID.randomUUID();
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));
        when(helper.canSeeCommunity(admin, communityId)).thenReturn(true);

        assertFalse(guard().canListPlants(communityId));
    }

    @Test
    void canListPlants_returnsTrue_whenUserIsMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.canSeeCommunity(user, communityId)).thenReturn(true);
        when(helper.hasMembershipInCommunity(user, communityId)).thenReturn(true);

        assertTrue(guard().canListPlants(communityId));
    }

    @Test
    void canListPlants_throwsNotFound_whenUserIsNotMemberOfPlantCommunity() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.canSeeCommunity(user, community.getId())).thenReturn(false);

        assertThrows(CommunityNotFoundException.class, () -> guard().canListPlants(community.getId()));
    }

    // --- canCreatePlant ---

    @Test
    void canCreatePlant_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canCreatePlant("ES001"));
    }

    @Test
    void canCreatePlant_returnsFalse_whenSupplyCodeIsNull() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(guard().canCreatePlant(null));
    }

    @Test
    void canCreatePlant_throwsNotFound_whenSupplyNotFoundByCode() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canCreatePlant(supplyCode));
    }

    @Test
    void canCreatePlant_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasCommunityAdminRoleIn(user, communityId)).thenReturn(true);

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
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

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
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canCreatePlant(supplyCode));
    }

    @Test
    void canCreatePlant_throwsNotFound_whenSupplyHasNoCommunity() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canCreatePlant(supplyCode));
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

    private Supply supplyInCommunityOwnedBy(User owner, Community community) {
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
}
