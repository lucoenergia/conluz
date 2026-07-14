package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class CreatePlantRepositoryDatabaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private PlantRepository plantRepository;

    @Test
    void shouldRejectDuplicateNonNullRegulatoryCode() {
        Supply supply = createSupply();
        String regulatoryCode = "ES0021000000000001JN0F";

        createPlantRepository.create(
                PlantMother.random(supply).withRegulatoryCode(regulatoryCode).build(),
                SupplyId.of(supply.getId()));

        assertThrows(DataIntegrityViolationException.class, () -> {
            createPlantRepository.create(
                    PlantMother.random(supply).withRegulatoryCode(regulatoryCode).build(),
                    SupplyId.of(supply.getId()));
            plantRepository.flush();
        });
    }

    @Test
    void shouldAllowMultiplePlantsWithNullRegulatoryCode() {
        Supply supply = createSupply();

        assertDoesNotThrow(() -> {
            createPlantRepository.create(
                    PlantMother.random(supply).withRegulatoryCode(null).build(),
                    SupplyId.of(supply.getId()));
            createPlantRepository.create(
                    PlantMother.random(supply).withRegulatoryCode(null).build(),
                    SupplyId.of(supply.getId()));
            plantRepository.flush();
        });
    }

    private Supply createSupply() {
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = SupplyMother.random(user).build();
        return createSupplyRepository.create(supply, UserId.of(user.getId()));
    }
}
