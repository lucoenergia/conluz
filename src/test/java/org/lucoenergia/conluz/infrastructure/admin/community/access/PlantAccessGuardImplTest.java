package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.access.PlantAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantAccessGuardImplTest {

    @Mock
    private CommunityAccessGuardHelper helper;
    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;

    private PlantAccessGuard guard() {
        return new PlantAccessGuardImpl(helper, getPlantRepository, getSupplyRepository);
    }

    // --- canManagePlant ---

    @Test
    void canManagePlant_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManagePlant(UUID.randomUUID()));
    }

    @Test
    void canManagePlant_returnsFalse_whenPlantNotFound() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        UUID plantId = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.empty());

        assertFalse(guard().canManagePlant(plantId));
    }

    @Test
    void canManagePlant_returnsFalse_whenUserIsPlatformAdminButNotCommunityAdmin() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));

        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder()
                .withId(UUID.randomUUID())
                .withSupply(supply)
                .build();
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertFalse(guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder()
                .withId(UUID.randomUUID())
                .withSupply(supply)
                .build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasCommunityAdminRoleIn(user, communityId)).thenReturn(true);
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertTrue(guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        Plant plant = new Plant.Builder()
                .withId(UUID.randomUUID())
                .withSupply(supply)
                .build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertFalse(guard().canManagePlant(plant.getId()));
    }

    @Test
    void canManagePlant_returnsFalse_whenPlantSupplyHasNoCommunity() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        Plant plant = new Plant.Builder()
                .withId(UUID.randomUUID())
                .withSupply(supply)
                .build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));

        assertFalse(guard().canManagePlant(plant.getId()));
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
    void canCreatePlant_returnsFalse_whenSupplyNotFoundByCode() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.empty());

        assertFalse(guard().canCreatePlant(supplyCode));
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
    void canCreatePlant_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertFalse(guard().canCreatePlant(supplyCode));
    }

    @Test
    void canCreatePlant_returnsFalse_whenSupplyHasNoCommunity() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        String supplyCode = "ES001";
        when(getSupplyRepository.findByCode(SupplyCode.of(supplyCode))).thenReturn(Optional.of(supply));

        assertFalse(guard().canCreatePlant(supplyCode));
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
}
