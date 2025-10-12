package org.lucoenergia.conluz.infrastructure.consumption.shelly.persist;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionMother;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionsInflux3Loader;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.get.GetShellyConsumptionRepositoryInflux3;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
class PersistShellyConsumptionRepositoryInflux3Test extends BaseIntegrationTest {

    @Autowired
    private ShellyInstantConsumptionsInflux3Loader shellyInstantConsumptionsInflux3Loader;

    @Autowired
    @Qualifier("persistShellyConsumptionRepositoryInflux3")
    private PersistShellyConsumptionRepositoryInflux3 persistShellyConsumptionRepositoryInflux3;

    @Autowired
    @Qualifier("getShellyConsumptionRepositoryInflux3")
    private GetShellyConsumptionRepositoryInflux3 getShellyConsumptionRepositoryInflux3;

    @AfterEach
    void afterEach() {
        shellyInstantConsumptionsInflux3Loader.clearData();
    }

    @Test
    void testPersistInstantConsumptions_success() {
        // Assemble
        Instant baseTime = Instant.now().plusSeconds(10000); // Use future time to avoid conflicts
        ShellyInstantConsumption consumptionOne = ShellyConsumptionMother.random()
                .withTimestamp(baseTime.plusSeconds(1)).build();
        ShellyInstantConsumption consumptionTwo = ShellyConsumptionMother.random()
                .withTimestamp(baseTime.plusSeconds(2)).build();
        ShellyInstantConsumption consumptionThree = ShellyConsumptionMother.random()
                .withTimestamp(baseTime.plusSeconds(3)).build();
        ShellyInstantConsumption consumptionFour = ShellyConsumptionMother.random()
                .withTimestamp(baseTime.plusSeconds(4)).build();
        List<ShellyInstantConsumption> consumptions = Arrays.asList(
                consumptionOne,
                consumptionTwo,
                consumptionThree,
                consumptionFour
        );

        // Act
        persistShellyConsumptionRepositoryInflux3.persistInstantConsumptions(consumptions);

        // Assert - Query all data and filter by timestamp range to verify our data
        List<ShellyInstantConsumption> allResults = getShellyConsumptionRepositoryInflux3.getAllInstantConsumptions();
        List<ShellyInstantConsumption> result = allResults.stream()
                .filter(c -> c.getTimestamp().isAfter(baseTime.minusSeconds(1))
                        && c.getTimestamp().isBefore(baseTime.plusSeconds(10)))
                .toList();

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

    @Test
    void testPersistInstantConsumptionsVerifyTimestamps() {
        // Assemble
        Instant baseTime = Instant.now().plusSeconds(20000); // Use far future time to avoid conflicts
        Instant timestamp1 = baseTime.plusSeconds(100);
        Instant timestamp2 = baseTime.plusSeconds(200);

        ShellyInstantConsumption consumption1 = ShellyConsumptionMother.random()
                .withTimestamp(timestamp1).build();
        ShellyInstantConsumption consumption2 = ShellyConsumptionMother.random()
                .withTimestamp(timestamp2).build();

        List<ShellyInstantConsumption> consumptions = Arrays.asList(consumption1, consumption2);

        // Act
        persistShellyConsumptionRepositoryInflux3.persistInstantConsumptions(consumptions);

        // Assert - Query all data and filter by timestamp range
        List<ShellyInstantConsumption> allResults = getShellyConsumptionRepositoryInflux3.getAllInstantConsumptions();
        List<ShellyInstantConsumption> result = allResults.stream()
                .filter(c -> c.getTimestamp().isAfter(baseTime.minusSeconds(1))
                        && c.getTimestamp().isBefore(baseTime.plusSeconds(300)))
                .toList();

        Assertions.assertEquals(2, result.size());

        // Verify timestamps are persisted correctly (allowing some tolerance)
        Assertions.assertTrue(result.stream()
                .anyMatch(r -> Math.abs(r.getTimestamp().toEpochMilli() - timestamp1.toEpochMilli()) < 1000));
        Assertions.assertTrue(result.stream()
                .anyMatch(r -> Math.abs(r.getTimestamp().toEpochMilli() - timestamp2.toEpochMilli()) < 1000));
    }

    @Test
    void testPersistEmptyList() {
        // Arrange
        Instant baseTime = Instant.now().plusSeconds(30000); // Use unique time range

        // Act
        persistShellyConsumptionRepositoryInflux3.persistInstantConsumptions(List.of());

        // Assert - Should not throw exception and no data should be in this specific time range
        List<ShellyInstantConsumption> allResults = getShellyConsumptionRepositoryInflux3.getAllInstantConsumptions();
        List<ShellyInstantConsumption> result = allResults.stream()
                .filter(c -> c.getTimestamp().isAfter(baseTime.minusSeconds(1))
                        && c.getTimestamp().isBefore(baseTime.plusSeconds(100)))
                .toList();
        Assertions.assertTrue(result.isEmpty());
    }
}
