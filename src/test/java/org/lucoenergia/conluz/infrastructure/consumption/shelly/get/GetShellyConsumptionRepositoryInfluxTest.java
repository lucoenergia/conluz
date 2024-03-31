package org.lucoenergia.conluz.infrastructure.consumption.shelly.get;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.*;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionsInfluxLoader;
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
    private ShellyConsumptionsInfluxLoader shellyConsumptionsInfluxLoader;
    @Autowired
    private GetShellyConsumptionRepositoryInflux getShellyConsumptionRepositoryInflux;

    @BeforeEach
    void beforeEach() {
        shellyConsumptionsInfluxLoader.loadData();
    }

    @AfterEach
    void afterEach() {
        shellyConsumptionsInfluxLoader.clearData();
    }

    @Disabled
    @Test
    void testGetHourlyConsumptionsByMonth() {
        // Assemble
        Supply supply = SupplyMother.random().withShellyMqttPrefix("s87sd56df9d9/ccc").build();

        // Act
        List<ShellyInstantConsumption> result = getShellyConsumptionRepositoryInflux.getHourlyConsumptionsByRangeOfDatesAndSupply(supply,
                START_DATE, END_DATE);

        // Assert
        Assertions.assertEquals(24, result.size());
    }
}