package org.lucoenergia.conluz.infrastructure.consumption.datadis.persist;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.ConsumptionMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.get.GetDatadisConsumptionRepositoryInflux3;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.util.Collections;
import java.util.List;

@Transactional
class PersistDatadisConsumptionRepositoryInfluxIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PersistDatadisConsumptionRepositoryInflux3 repository;
    @Autowired
    private GetDatadisConsumptionRepositoryInflux3 getDatadisConsumptionRepositoryInflux;

    @Disabled
    @Test
    void testPersistSingleConsumption() throws InterruptedException {
        // Arrange
        Month month = Month.OCTOBER;
        int year = 2024;
        DatadisConsumption consumption = ConsumptionMother.random();
        consumption.setDate(String.format("%s/%02d/01", year, month.getValue()));
        Supply supply = new Supply.Builder().withCode(consumption.getCups()).build();

        // Act
        repository.persistConsumptions(Collections.singletonList(consumption));

        // Assert
        Thread.sleep(10_000L);
        List<DatadisConsumption> consumptions = getDatadisConsumptionRepositoryInflux.getHourlyConsumptionsByMonth(supply, month, year);

        Assertions.assertFalse(consumptions.isEmpty());
        Assertions.assertEquals(1, consumptions.size());
        DatadisConsumption persistedConsumption = consumptions.get(0);
        Assertions.assertEquals(consumption.getCups(), persistedConsumption.getCups());
        Assertions.assertEquals(String.format("%s/%s", year, month.getValue()), persistedConsumption.getDate());
        Assertions.assertEquals(consumption.getTime(), persistedConsumption.getTime());
        Assertions.assertEquals(consumption.getConsumptionKWh(), persistedConsumption.getConsumptionKWh());
        Assertions.assertEquals(consumption.getObtainMethod(), persistedConsumption.getObtainMethod());
        Assertions.assertEquals(consumption.getSurplusEnergyKWh(), persistedConsumption.getSurplusEnergyKWh());
    }
}