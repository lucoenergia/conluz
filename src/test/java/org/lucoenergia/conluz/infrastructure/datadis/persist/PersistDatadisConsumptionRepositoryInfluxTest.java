package org.lucoenergia.conluz.infrastructure.datadis.persist;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.consumption.datadis.ConsumptionMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class PersistDatadisConsumptionRepositoryInfluxTest {

    @InjectMocks
    private PersistDatadisConsumptionRepositoryInflux repository;

    @Mock
    private InfluxDbConnectionManager influxDbConnectionManager;

    @Mock
    private InfluxDB influxDB;
    @Mock
    private BatchPoints batchPoints;

    @Test
    void testPersistSingleConsumptions() {
        // Arrange
        DatadisConsumption consumption = Mockito.spy(ConsumptionMother.random());
        Mockito.when(influxDbConnectionManager.getConnection()).thenReturn(influxDB);
        Mockito.when(influxDbConnectionManager.createBatchPoints()).thenReturn(batchPoints);

        // Act
        repository.persistHourlyConsumptions(Collections.singletonList(
                new DatadisHourlyRecord<>(Instant.parse("2024-01-10T09:00:00Z"), consumption)));

        // Assert
        Mockito.verify(consumption, Mockito.atLeastOnce()).getCups();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getConsumptionKWh();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getObtainMethod();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getSurplusEnergyKWh();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getGenerationEnergyKWh();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getSelfConsumptionEnergyKWh();
    }
}