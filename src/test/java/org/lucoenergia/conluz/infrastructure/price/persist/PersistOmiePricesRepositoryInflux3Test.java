package org.lucoenergia.conluz.infrastructure.price.persist;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistOmiePricesRepositoryInflux3Test {

    @InjectMocks
    private PersistOmiePricesRepositoryInflux3 repository;

    @Mock
    private InfluxDb3ConnectionManager connectionManager;

    @Mock
    private InfluxDBClient client;

    @Test
    void testPersistSinglePrice() {
        // Arrange
        OffsetDateTime hour = OffsetDateTime.parse("2023-10-25T10:00:00.000+02:00");
        PriceByHour price = new PriceByHour(100.5d, hour);

        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistPrices(Collections.singletonList(price));

        // Assert
        verify(client, times(1)).writePoints(any(List.class));
    }

    @Test
    void testPersistMultiplePrices() {
        // Arrange
        PriceByHour price1 = new PriceByHour(100.5d, OffsetDateTime.parse("2023-10-25T10:00:00.000+02:00"));
        PriceByHour price2 = new PriceByHour(95.3d, OffsetDateTime.parse("2023-10-25T11:00:00.000+02:00"));

        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistPrices(Arrays.asList(price1, price2));

        // Assert
        verify(client, times(1)).writePoints(any(List.class));
    }

    @Test
    void testPersistPricesWithCorrectTimestamp() {
        // Arrange
        OffsetDateTime hour = OffsetDateTime.parse("2023-10-25T14:00:00.000+02:00");
        PriceByHour price = new PriceByHour(120.75d, hour);

        when(connectionManager.getClient()).thenReturn(client);

        ArgumentCaptor<List<Point>> pointsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        repository.persistPrices(Collections.singletonList(price));

        // Assert
        verify(client).writePoints(pointsCaptor.capture());

        List<Point> capturedPoints = pointsCaptor.getValue();
        assertEquals(1, capturedPoints.size());
        Point capturedPoint = capturedPoints.get(0);
        assertNotNull(capturedPoint, "Point should not be null");
    }

    @Test
    void testPersistPricesWithAllFields() {
        // Arrange
        OffsetDateTime hour = OffsetDateTime.parse("2023-10-25T15:00:00.000+02:00");
        PriceByHour price = new PriceByHour(150.25d, hour);

        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistPrices(Collections.singletonList(price));

        // Assert
        verify(client).writePoints(any(List.class));
        assertEquals(150.25d, price.getPrice());
        assertEquals(hour, price.getHour());
    }

    @Test
    void testPersistEmptyList() {
        // Arrange
        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistPrices(Collections.emptyList());

        // Assert
        verify(client, times(0)).writePoints(any(List.class));
    }
}
