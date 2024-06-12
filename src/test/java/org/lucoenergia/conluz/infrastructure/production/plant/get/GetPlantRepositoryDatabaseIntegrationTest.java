package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}