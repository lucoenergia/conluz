package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.consumption.datadis.ConsumptionMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.persist.PersistDatadisConsumptionRepositoryInflux;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateToMillisecondsConverter;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class PersistDatadisConsumptionRepositoryInfluxTest {

    @InjectMocks
    private PersistDatadisConsumptionRepositoryInflux repository;

    @Mock
    private InfluxDbConnectionManager influxDbConnectionManager;

    @Mock
    private DateToMillisecondsConverter dateToMillisecondsConverter;

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
        repository.persistConsumptions(Collections.singletonList(consumption));

        // Assert
        Mockito.verify(consumption, Mockito.atLeastOnce()).getCups();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getConsumptionKWh();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getObtainMethod();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getSurplusEnergyKWh();
    }
}