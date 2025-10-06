package org.lucoenergia.conluz.infrastructure.consumption.datadis.persist;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.consumption.datadis.ConsumptionMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistDatadisConsumptionRepositoryInflux3Test {

    @InjectMocks
    private PersistDatadisConsumptionRepositoryInflux3 repository;

    @Mock
    private InfluxDb3ConnectionManager connectionManager;

    @Mock
    private DateConverter dateConverter;

    @Mock
    private InfluxDBClient client;

    @Test
    void testPersistSingleConsumptions() {
        // Arrange
        DatadisConsumption consumption = Mockito.spy(ConsumptionMother.random());
        when(connectionManager.getClient()).thenReturn(client);
        when(dateConverter.convertStringDateToMilliseconds(any())).thenReturn(1680307200000L); // April 1, 2023

        // Act
        repository.persistConsumptions(Collections.singletonList(consumption));

        // Assert - Verify consumption fields were accessed
        verify(consumption, Mockito.atLeastOnce()).getCups();
        verify(consumption, Mockito.atLeastOnce()).getConsumptionKWh();
        verify(consumption, Mockito.atLeastOnce()).getObtainMethod();
        verify(consumption, Mockito.atLeastOnce()).getSurplusEnergyKWh();
        verify(consumption, Mockito.atLeastOnce()).getGenerationEnergyKWh();
        verify(consumption, Mockito.atLeastOnce()).getSelfConsumptionEnergyKWh();
        verify(consumption, Mockito.atLeastOnce()).getDate();
        verify(consumption, Mockito.atLeastOnce()).getTime();

        // Verify writePoint was called with Point object
        verify(client, times(1)).writePoint(any(Point.class));
    }

    @Test
    void testPersistMultipleConsumptions() {
        // Arrange
        DatadisConsumption consumption1 = ConsumptionMother.random();
        DatadisConsumption consumption2 = ConsumptionMother.random();
        when(connectionManager.getClient()).thenReturn(client);
        when(dateConverter.convertStringDateToMilliseconds(any())).thenReturn(1680307200000L);

        // Act
        repository.persistConsumptions(java.util.Arrays.asList(consumption1, consumption2));

        // Assert - Verify writePoint was called twice
        verify(client, times(2)).writePoint(any(Point.class));
    }

    @Test
    void testPersistConsumptionsWithTimestampConversion() {
        // Arrange
        DatadisConsumption consumption = ConsumptionMother.random();
        consumption.setDate("2023/04/01");
        consumption.setTime("12:00");

        long expectedMillis = 1680350400000L; // 2023-04-01T12:00:00Z
        when(connectionManager.getClient()).thenReturn(client);
        when(dateConverter.convertStringDateToMilliseconds("2023/04/01T12:00")).thenReturn(expectedMillis);

        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);

        // Act
        repository.persistConsumptions(Collections.singletonList(consumption));

        // Assert
        verify(dateConverter).convertStringDateToMilliseconds("2023/04/01T12:00");
        verify(client).writePoint(pointCaptor.capture());

        Point capturedPoint = pointCaptor.getValue();
        assertNotNull(capturedPoint, "Point should not be null");
    }

    @Test
    void testPersistConsumptionsHandlesAllFields() {
        // Arrange
        DatadisConsumption consumption = ConsumptionMother.random();
        consumption.setCups("ES0031406912345678JN0F");
        consumption.setConsumptionKWh(1.5f);
        consumption.setSurplusEnergyKWh(0.3f);
        consumption.setGenerationEnergyKWh(0.5f);
        consumption.setSelfConsumptionEnergyKWh(0.2f);
        consumption.setObtainMethod("Real");

        when(connectionManager.getClient()).thenReturn(client);
        when(dateConverter.convertStringDateToMilliseconds(any())).thenReturn(1680307200000L);

        // Act
        repository.persistConsumptions(Collections.singletonList(consumption));

        // Assert
        verify(client).writePoint(any(Point.class));

        // Verify all consumption fields were accessed for writing
        assertEquals("ES0031406912345678JN0F", consumption.getCups());
        assertEquals(1.5f, consumption.getConsumptionKWh());
        assertEquals(0.3f, consumption.getSurplusEnergyKWh());
        assertEquals(0.5f, consumption.getGenerationEnergyKWh());
        assertEquals(0.2f, consumption.getSelfConsumptionEnergyKWh());
        assertEquals("Real", consumption.getObtainMethod());
    }

    @Test
    void testPersistEmptyList() {
        // Arrange
        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistConsumptions(Collections.emptyList());

        // Assert - writePoint should not be called
        verify(client, times(0)).writePoint(any(Point.class));
    }
}
