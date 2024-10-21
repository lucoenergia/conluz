package org.lucoenergia.conluz.infrastructure.consumption.shelly.get;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.*;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionsInfluxLoader;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyMqttPowerMessagesInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Transactional
class GetShellyConsumptionRepositoryInfluxTest extends BaseIntegrationTest {

    private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2023-10-24T00:00:00.000+00:00");
    private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2023-10-26T00:00:00.000+00:00");

    @Autowired
    private ShellyInstantConsumptionsInfluxLoader shellyInstantConsumptionsInfluxLoader;
    @Autowired
    private ShellyMqttPowerMessagesInfluxLoader shellyMqttPowerMessagesInfluxLoader;
    @Autowired
    private GetShellyConsumptionRepositoryInflux getShellyConsumptionRepositoryInflux;

    @Test
    void testGetHourlyConsumptionsByMonth() {
        // Assemble
        shellyInstantConsumptionsInfluxLoader.loadData();
        Supply supply = SupplyMother.random().withShellyMqttPrefix("s87sd56df9d9/ccc").build();

        // Act
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux.getHourlyConsumptionsByRangeOfDatesAndSupply(supply,
                START_DATE, END_DATE);

        // Assert
        Assertions.assertEquals(10, result.size());

        // Cleanup
        shellyInstantConsumptionsInfluxLoader.clearData();
    }

    @Test
    void testGetShellyMqttPowerMessagesByRangeOfDates() {
        // Assemble
        shellyMqttPowerMessagesInfluxLoader.loadData();

        // Act
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux.getShellyMqttPowerMessagesByRangeOfDates(
                START_DATE, END_DATE);

        // Assert
        Assertions.assertEquals(37, result.size());
        Assertions.assertEquals(0.1141d, result.get(0).getConsumptionKW());
        Assertions.assertEquals("0", result.get(0).getChannel());
        Assertions.assertEquals("70c590f9f395fbae/vcm", result.get(0).getPrefix());

        // Cleanup
        shellyMqttPowerMessagesInfluxLoader.clearData();
    }
}