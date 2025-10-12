package org.lucoenergia.conluz.infrastructure.consumption.shelly.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionsInflux3Loader;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.get.GetShellyConsumptionRepositoryInflux3;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionsInflux3Loader.*;

@SpringBootTest
@Transactional
class ShellyConsumptionsHourlyAggregatorInflux3ServiceTest extends BaseIntegrationTest {

    private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2023-10-24T00:00:00.000+00:00");
    private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2023-10-26T00:00:00.000+00:00");

    @Autowired
    private ShellyInstantConsumptionsInflux3Loader shellyInstantConsumptionsInflux3Loader;

    @Autowired
    @Qualifier("shellyConsumptionsHourlyAggregatorInflux3Service")
    private ShellyConsumptionsHourlyAggregatorInflux3Service shellyConsumptionsHourlyAggregatorInflux3Service;

    @Autowired
    @Qualifier("getShellyConsumptionRepositoryInflux3")
    private GetShellyConsumptionRepositoryInflux3 getShellyConsumptionRepositoryInflux3;

    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    @Autowired
    private CreateUserRepository createUserRepository;

    @BeforeEach
    void beforeEach() {
        shellyInstantConsumptionsInflux3Loader.loadData();
    }

    @AfterEach
    void afterEach() {
        shellyInstantConsumptionsInflux3Loader.clearData();
    }

    @Test
    void testAggregateConsumptionsHourly() {
        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        createSupplyRepository.create(SupplyMother.random(user)
                .withShellyMqttPrefix(SUPPLY_A_MQTT_PREFIX).build(), UserId.of(user.getId()));
        createSupplyRepository.create(SupplyMother.random(user)
                .withShellyMqttPrefix(SUPPLY_B_MQTT_PREFIX).build(), UserId.of(user.getId()));

        // When
        shellyConsumptionsHourlyAggregatorInflux3Service.aggregate(START_DATE, END_DATE);

        // Then
        List<ShellyConsumption> result = getShellyConsumptionRepositoryInflux3.getAllConsumptions();
        Assertions.assertFalse(result.isEmpty());
    }

    @Test
    void testAggregateConsumptionsHourlyWithMultipleSupplies() {
        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supplyA = SupplyMother.random(user)
                .withShellyMqttPrefix(SUPPLY_A_MQTT_PREFIX).build();
        Supply supplyB = SupplyMother.random(user)
                .withShellyMqttPrefix(SUPPLY_B_MQTT_PREFIX).build();
        Supply supplyC = SupplyMother.random(user)
                .withShellyMqttPrefix(SUPPLY_C_MQTT_PREFIX).build();

        createSupplyRepository.create(supplyA, UserId.of(user.getId()));
        createSupplyRepository.create(supplyB, UserId.of(user.getId()));
        createSupplyRepository.create(supplyC, UserId.of(user.getId()));

        // When
        shellyConsumptionsHourlyAggregatorInflux3Service.aggregate(START_DATE, END_DATE);

        // Then
        List<ShellyConsumption> result = getShellyConsumptionRepositoryInflux3.getAllConsumptions();
        Assertions.assertFalse(result.isEmpty());

        // Verify that consumptions were aggregated for all supplies
        boolean hasSupplyAData = result.stream()
                .anyMatch(c -> SUPPLY_A_MQTT_PREFIX.equals(c.getPrefix()));
        boolean hasSupplyBData = result.stream()
                .anyMatch(c -> SUPPLY_B_MQTT_PREFIX.equals(c.getPrefix()));
        boolean hasSupplyCData = result.stream()
                .anyMatch(c -> SUPPLY_C_MQTT_PREFIX.equals(c.getPrefix()));

        Assertions.assertTrue(hasSupplyAData, "Should have aggregated data for Supply A");
        Assertions.assertTrue(hasSupplyBData, "Should have aggregated data for Supply B");
        Assertions.assertTrue(hasSupplyCData, "Should have aggregated data for Supply C");
    }

    @Test
    void testAggregateConsumptionsHourlyNoSupplies() {
        // When - Run aggregation with no supplies created
        shellyConsumptionsHourlyAggregatorInflux3Service.aggregate(START_DATE, END_DATE);

        // Then - Should complete without error
        List<ShellyConsumption> result = getShellyConsumptionRepositoryInflux3.getAllConsumptions();
        Assertions.assertTrue(result.isEmpty());
    }
}
