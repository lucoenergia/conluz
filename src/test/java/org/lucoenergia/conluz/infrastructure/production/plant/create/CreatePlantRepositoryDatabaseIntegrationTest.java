package org.lucoenergia.conluz.infrastructure.production.plant.create;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.Plant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

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
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldStoreThePlantAttachedToItsSupply() {
        Supply supply = createSupply();
        Plant plant = PlantMother.random(supply).build();

        Plant result = createPlantRepository.create(plant, SupplyId.of(supply.getId()));
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM plants WHERE id = ?", result.getId());
        assertEquals(supply.getId(), row.get("supply_id"));
        assertEquals(plant.getProviderCode(), row.get("provider_code"));
        assertEquals(plant.getRegulatoryCode(), row.get("regulatory_code"));
        assertEquals(plant.getName(), row.get("name"));
        assertEquals(plant.getAddress(), row.get("address"));
        assertEquals(plant.getDescription(), row.get("description"));
        assertEquals(plant.getInverterProvider().name(), row.get("inverter_provider"));
        assertEquals(plant.getTotalPower(), row.get("total_power"));
        assertEquals(Date.valueOf(plant.getConnectionDate()), row.get("connection_date"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM plants WHERE supply_id = ?", Integer.class,
                supply.getId()));
    }

    @Test
    void shouldRejectDuplicateNonNullRegulatoryCode() {
        Supply supply = createSupply();
        String regulatoryCode = "ES0021000000000001JN0F";

        createPlantRepository.create(
                PlantMother.random(supply).withRegulatoryCode(regulatoryCode).build(),
                SupplyId.of(supply.getId()));

        // A different supply, so the only rule the second plant breaks is the regulatory code's
        Supply otherSupply = createSupply();
        assertThrows(DataIntegrityViolationException.class, () -> {
            createPlantRepository.create(
                    PlantMother.random(otherSupply).withRegulatoryCode(regulatoryCode).build(),
                    SupplyId.of(otherSupply.getId()));
            plantRepository.flush();
        });
    }

    @Test
    void shouldAllowMultiplePlantsWithNullRegulatoryCode() {
        Supply supply = createSupply();
        // A supply is the supply of at most one plant (PLT-002), so the second plant gets its own
        Supply otherSupply = createSupply();

        assertDoesNotThrow(() -> {
            createPlantRepository.create(
                    PlantMother.random(supply).withRegulatoryCode(null).build(),
                    SupplyId.of(supply.getId()));
            createPlantRepository.create(
                    PlantMother.random(otherSupply).withRegulatoryCode(null).build(),
                    SupplyId.of(otherSupply.getId()));
            plantRepository.flush();
        });
    }

    private Supply createSupply() {
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = SupplyMother.random(user).build();
        return createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);
    }
}
