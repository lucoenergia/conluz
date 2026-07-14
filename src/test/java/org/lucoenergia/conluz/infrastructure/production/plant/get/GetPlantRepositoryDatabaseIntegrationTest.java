package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class GetPlantRepositoryDatabaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetPlantRepositoryDatabase getPlantRepositoryDatabase;

    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;


    @Test
    void testFindAllWithZeroResults() {

        PagedRequest pagedRequest = PagedRequest.of(0, 10);
        PagedResult<Plant> pagedResult = getPlantRepositoryDatabase.findAll(pagedRequest);

        assertEquals(0, pagedResult.getItems().size());
    }

    @Test
    void testFindAllWithMoreThanOneResults() {

        User user = UserMother.randomUser();
        user = createUserRepository.create(user);
        Supply supply = SupplyMother.random(user).build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));
        Plant plantEntityOne = PlantMother.random(supply).build();
        createPlantRepository.create(plantEntityOne, SupplyId.of(supply.getId()));
        Plant plantEntityTwo = PlantMother.random(supply).build();
        createPlantRepository.create(plantEntityTwo, SupplyId.of(supply.getId()));

        PagedRequest pagedRequest = PagedRequest.of(0, 10);
        PagedResult<Plant> pagedResult = getPlantRepositoryDatabase.findAll(pagedRequest);

        assertEquals(2, pagedResult.getItems().size());
    }

    @Test
    void testFindByIdReturnsThePlantWhenItExists() {

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));

        Optional<Plant> result = getPlantRepositoryDatabase.findById(PlantId.of(plant.getId()));

        assertTrue(result.isPresent());
        assertEquals(plant.getId(), result.get().getId());
        assertEquals(plant.getProviderCode(), result.get().getProviderCode());
    }

    @Test
    void testFindByIdReturnsEmptyWhenThePlantDoesNotExist() {

        Optional<Plant> result = getPlantRepositoryDatabase.findById(PlantId.of(UUID.randomUUID()));

        assertFalse(result.isPresent());
    }

    @Test
    void testFindByCommunitiesReturnsOnlyPlantsOfTheGivenCommunities() {

        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        User user = createUserRepository.create(UserMother.randomUser());

        Supply supplyInCommunity = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        Supply supplyInOtherCommunity = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), otherCommunity.getId());

        Plant plantInCommunity = createPlantRepository.create(PlantMother.random(supplyInCommunity).build(),
                SupplyId.of(supplyInCommunity.getId()));
        Plant plantInOtherCommunity = createPlantRepository.create(PlantMother.random(supplyInOtherCommunity).build(),
                SupplyId.of(supplyInOtherCommunity.getId()));

        PagedResult<Plant> result = getPlantRepositoryDatabase.findByCommunities(PagedRequest.of(0, 10),
                Set.of(community.getId()));

        assertEquals(1, result.getTotalElements());
        List<UUID> ids = result.getItems().stream().map(Plant::getId).collect(Collectors.toList());
        assertTrue(ids.contains(plantInCommunity.getId()));
        assertFalse(ids.contains(plantInOtherCommunity.getId()));
    }

    @Test
    void testFindByCommunitiesReturnsEmptyWhenNoPlantBelongsToTheCommunities() {

        Community community = createCommunityRepository.create(CommunityMother.random().build());

        PagedResult<Plant> result = getPlantRepositoryDatabase.findByCommunities(PagedRequest.of(0, 10),
                Set.of(community.getId()));

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void testFindByCommunitiesRespectsPagination() {

        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUserRepository.create(UserMother.randomUser());

        for (int i = 0; i < 3; i++) {
            Supply supply = createSupplyRepository.create(SupplyMother.random(user).build(),
                    UserId.of(user.getId()), community.getId());
            createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        }

        PagedResult<Plant> firstPage = getPlantRepositoryDatabase.findByCommunities(PagedRequest.of(0, 2),
                Set.of(community.getId()));
        PagedResult<Plant> secondPage = getPlantRepositoryDatabase.findByCommunities(PagedRequest.of(1, 2),
                Set.of(community.getId()));

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(2, firstPage.getItems().size());
        assertEquals(1, secondPage.getItems().size());
    }

    @Test
    void testFindPlantProviderCodesByCommunityReturnsCodesOfPlantsOfThatCommunity() {

        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        User user = createUserRepository.create(UserMother.randomUser());

        Supply supplyInCommunity = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        Supply supplyInOtherCommunity = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), otherCommunity.getId());

        Plant plantInCommunity = createPlantRepository.create(PlantMother.random(supplyInCommunity).build(),
                SupplyId.of(supplyInCommunity.getId()));
        createPlantRepository.create(PlantMother.random(supplyInOtherCommunity).build(),
                SupplyId.of(supplyInOtherCommunity.getId()));

        List<String> codes = getPlantRepositoryDatabase.findPlantProviderCodesByCommunity(community.getId());

        assertEquals(1, codes.size());
        assertTrue(codes.contains(plantInCommunity.getProviderCode()));
    }

    @Test
    void testFindPlantProviderCodesByCommunityReturnsEmptyWhenCommunityHasNoPlants() {

        Community community = createCommunityRepository.create(CommunityMother.random().build());

        List<String> codes = getPlantRepositoryDatabase.findPlantProviderCodesByCommunity(community.getId());

        assertTrue(codes.isEmpty());
    }

    @Test
    void testFindSupplyCodesByCommunityReturnsCupsOfPlantsOfThatCommunity() {

        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        User user = createUserRepository.create(UserMother.randomUser());

        Supply supplyInCommunity = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        Supply supplyInOtherCommunity = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), otherCommunity.getId());

        Plant plantInCommunity = createPlantRepository.create(PlantMother.random(supplyInCommunity).build(),
                SupplyId.of(supplyInCommunity.getId()));
        createPlantRepository.create(PlantMother.random(supplyInOtherCommunity).build(),
                SupplyId.of(supplyInOtherCommunity.getId()));

        Set<String> codes = getPlantRepositoryDatabase.findSupplyCodesByCommunity(community.getId());

        assertEquals(1, codes.size());
        // The CUPS (supply code) is returned, not the plant/station code.
        assertTrue(codes.contains(supplyInCommunity.getCode()));
        assertFalse(codes.contains(plantInCommunity.getProviderCode()));
        assertFalse(codes.contains(supplyInOtherCommunity.getCode()));
    }

    @Test
    void testFindSupplyCodesByCommunityReturnsEmptyWhenCommunityHasNoPlants() {

        Community community = createCommunityRepository.create(CommunityMother.random().build());

        Set<String> codes = getPlantRepositoryDatabase.findSupplyCodesByCommunity(community.getId());

        assertTrue(codes.isEmpty());
    }
}
