package org.lucoenergia.conluz.infrastructure.consumption.shelly.persist;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionMother;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionsInfluxLoader;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.get.GetShellyConsumptionRepositoryInflux;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

class PersistShellyConsumptionRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    private ShellyInstantConsumptionsInfluxLoader shellyInstantConsumptionsInfluxLoader;
    @Autowired
    private PersistShellyConsumptionRepositoryInflux persistShellyConsumptionRepositoryInflux;
    @Autowired
    private GetShellyConsumptionRepositoryInflux getShellyConsumptionRepositoryInflux;

    @AfterEach
    void afterEach() {
        shellyInstantConsumptionsInfluxLoader.clearData();
    }

    @Test
    void testPersistConsumptions_success() {
        // Assemble
        ShellyInstantConsumption consumptionOne = ShellyConsumptionMother.random()
                .withTimestamp(Instant.now().plusSeconds(1)).build();
        ShellyInstantConsumption consumptionTwo = ShellyConsumptionMother.random()
                .withTimestamp(Instant.now().plusSeconds(2)).build();
        ShellyInstantConsumption consumptionThree = ShellyConsumptionMother.random()
                .withTimestamp(Instant.now().plusSeconds(3)).build();
        ShellyInstantConsumption consumptionFour = ShellyConsumptionMother.random()
                .withTimestamp(Instant.now().plusSeconds(4)).build();
        List<ShellyInstantConsumption> consumptions = Arrays.asList(
                consumptionOne,
                consumptionTwo,
                consumptionThree,
                consumptionFour
        );

        // Act
        persistShellyConsumptionRepositoryInflux.persistInstantConsumptions(consumptions);

        // Assert
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux.getAllInstantConsumptions();
        Assertions.assertEquals(consumptions.size(), result.size());

        Assertions.assertEquals(result.get(0).getConsumptionKW(), consumptionOne.getConsumptionKW());
        Assertions.assertEquals(result.get(0).getPrefix(), consumptionOne.getPrefix());
        Assertions.assertEquals(result.get(0).getChannel(), consumptionOne.getChannel());

        Assertions.assertEquals(result.get(1).getConsumptionKW(), consumptionTwo.getConsumptionKW());
        Assertions.assertEquals(result.get(1).getPrefix(), consumptionTwo.getPrefix());
        Assertions.assertEquals(result.get(1).getChannel(), consumptionTwo.getChannel());

        Assertions.assertEquals(result.get(2).getConsumptionKW(), consumptionThree.getConsumptionKW());
        Assertions.assertEquals(result.get(2).getPrefix(), consumptionThree.getPrefix());
        Assertions.assertEquals(result.get(2).getChannel(), consumptionThree.getChannel());

        Assertions.assertEquals(result.get(3).getConsumptionKW(), consumptionFour.getConsumptionKW());
        Assertions.assertEquals(result.get(3).getPrefix(), consumptionFour.getPrefix());
        Assertions.assertEquals(result.get(3).getChannel(), consumptionFour.getChannel());

    }
}