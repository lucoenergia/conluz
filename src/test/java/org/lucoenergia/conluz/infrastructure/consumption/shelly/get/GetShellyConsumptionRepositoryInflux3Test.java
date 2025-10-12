package org.lucoenergia.conluz.infrastructure.consumption.shelly.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionsInflux3Loader;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyMqttPowerMessagesInflux3Loader;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
@Transactional
class GetShellyConsumptionRepositoryInflux3Test extends BaseIntegrationTest {

    private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2023-10-24T00:00:00.000+00:00");
    private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2023-10-26T00:00:00.000+00:00");

    @Autowired
    private ShellyInstantConsumptionsInflux3Loader shellyInstantConsumptionsInflux3Loader;

    @Autowired
    private ShellyMqttPowerMessagesInflux3Loader shellyMqttPowerMessagesInflux3Loader;

    @Autowired
    @Qualifier("getShellyConsumptionRepositoryInflux3")
    private GetShellyConsumptionRepositoryInflux3 getShellyConsumptionRepositoryInflux3;

    @BeforeEach
    void beforeEach() {
        shellyInstantConsumptionsInflux3Loader.loadData();
        shellyMqttPowerMessagesInflux3Loader.loadData();
    }

    @AfterEach
    void afterEach() {
        shellyInstantConsumptionsInflux3Loader.clearData();
        shellyMqttPowerMessagesInflux3Loader.clearData();
    }

    @Test
    void testGetHourlyConsumptionsByRangeOfDatesAndSupply() {
        // Assemble
        Supply supply = SupplyMother.random().withShellyMqttPrefix("s87sd56df9d9/ccc").build();

        // Act
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux3
                .getHourlyConsumptionsByRangeOfDatesAndSupply(supply, START_DATE, END_DATE);

        // Assert
        Assertions.assertEquals(10, result.size());
    }

    @Test
    void testGetAllInstantConsumptions() {
        // Act
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux3.getAllInstantConsumptions();

        // Assert
        Assertions.assertFalse(result.isEmpty());
        // We loaded 49 instant consumptions in the loader
        Assertions.assertEquals(49, result.size());
    }

    @Test
    void testGetShellyMqttPowerMessagesByRangeOfDates() {
        // Act
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux3
                .getShellyMqttPowerMessagesByRangeOfDates(START_DATE, END_DATE);

        // Assert
        Assertions.assertEquals(37, result.size());

        // Verify first message
        ShellyInstantConsumption firstMessage = result.get(0);
        Assertions.assertEquals(0.1141d, firstMessage.getConsumptionKW(), 0.0001d);
        Assertions.assertEquals("0", firstMessage.getChannel());
        Assertions.assertEquals("70c590f9f395fbae/vcm", firstMessage.getPrefix());
    }

    @Test
    void testGetHourlyConsumptionsByRangeOfDatesAndSupplyEmptyResult() {
        // Assemble - Use a prefix that doesn't exist in test data
        Supply supply = SupplyMother.random().withShellyMqttPrefix("nonexistent/prefix").build();

        // Act
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux3
                .getHourlyConsumptionsByRangeOfDatesAndSupply(supply, START_DATE, END_DATE);

        // Assert
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void testGetShellyMqttPowerMessagesByRangeOfDatesEmptyResult() {
        // Assemble - Query a date range with no data
        OffsetDateTime futureStartDate = OffsetDateTime.parse("2024-01-01T00:00:00.000+00:00");
        OffsetDateTime futureEndDate = OffsetDateTime.parse("2024-01-02T00:00:00.000+00:00");

        // Act
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux3
                .getShellyMqttPowerMessagesByRangeOfDates(futureStartDate, futureEndDate);

        // Assert
        Assertions.assertTrue(result.isEmpty());
    }
}
