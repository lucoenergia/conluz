package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.influxdb.InfluxDB;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.Consumption;
import org.lucoenergia.conluz.domain.consumption.datadis.ConsumptionMother;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateToMillisecondsConverter;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

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

    @Test
    void testPersistSingleConsumptions() {
        // Arrange
        Consumption consumption = Mockito.spy(ConsumptionMother.random());
        Mockito.when(influxDbConnectionManager.getConnection()).thenReturn(influxDB);

        // Act
        repository.persistConsumptions(Collections.singletonList(consumption));

        // Assert
        Mockito.verify(influxDB, Mockito.atLeastOnce()).write("datadis-consumption-kwh");
        Mockito.verify(consumption, Mockito.atLeastOnce()).getCups();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getConsumptionKWh();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getObtainMethod();
        Mockito.verify(consumption, Mockito.atLeastOnce()).getSurplusEnergyKWh();
    }
}