package org.lucoenergia.conluz.infrastructure.production.get;

import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Transactional
class GetEnergyStationRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetEnergyStationRepositoryDatabase getEnergyStationRepositoryDatabase;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindAllByInverterProvider() {
        UserEntity user = userRepository.save(UserMother.randomUserEntity());
        PlantEntity plantEntity1 = plantRepository.save(PlantMother.randomPlantEntity().withUser(user).build());
        PlantEntity plantEntity2 = plantRepository.save(PlantMother.randomPlantEntity().withUser(user).build());

        List<Plant> expectedPlants = getEnergyStationRepositoryDatabase.findAllByInverterProvider(InverterProvider.HUAWEI);

        Assertions.assertEquals(2, expectedPlants.size());
        Assertions.assertEquals(plantEntity1.getId(), expectedPlants.get(0).getId());
        Assertions.assertEquals(plantEntity2.getId(), expectedPlants.get(1).getId());
    }
}