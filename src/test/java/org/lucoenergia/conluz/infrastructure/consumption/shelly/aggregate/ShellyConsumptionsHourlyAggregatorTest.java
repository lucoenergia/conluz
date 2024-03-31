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
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConsumptionRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionsInfluxLoader;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.get.GetShellyConsumptionRepositoryInflux;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;

import static org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionsInfluxLoader.*;


class ShellyConsumptionsHourlyAggregatorTest extends BaseIntegrationTest {

    private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2023-10-24T00:00:00.000+00:00");
    private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2023-10-26T00:00:00.000+00:00");

    @Autowired
    private ShellyConsumptionsInfluxLoader shellyConsumptionsInfluxLoader;
    @Autowired
    private ShellyConsumptionsHourlyAggregator shellyConsumptionsHourlyAggregator;
    @Autowired
    private GetShellyConsumptionRepositoryInflux getShellyConsumptionRepositoryInflux;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;

    @BeforeEach
    void beforeEach() {
        shellyConsumptionsInfluxLoader.loadData();
    }

    @AfterEach
    void afterEach() {
        shellyConsumptionsInfluxLoader.clearData();
    }

    @Test
     void aggregateConsumptionsHourly() {
        //Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        final Supply supplyOne = createSupplyRepository.create(SupplyMother.random(user)
                .withShellyMqttPrefix(SUPPLY_A_MQTT_PREFIX).build(), UserId.of(user.getId()));
        final Supply supplyTwo = createSupplyRepository.create(SupplyMother.random(user)
                .withShellyMqttPrefix(SUPPLY_B_MQTT_PREFIX).build(), UserId.of(user.getId()));

        //When
        shellyConsumptionsHourlyAggregator.aggregate(START_DATE, END_DATE);

        //Then
        List<ShellyConsumption> result = getShellyConsumptionRepositoryInflux.getAllConsumptions();
        Assertions.assertFalse(result.isEmpty());
    }
}