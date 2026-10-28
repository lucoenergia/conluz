package org.lucoenergia.conluz.infrastructure.price.persist;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBIOException;
import org.influxdb.dto.BatchPoints;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistOmiePricesRepositoryTest {

    private final InfluxDbConnectionManager connectionManager = mock(InfluxDbConnectionManager.class);
    private final InfluxDB connection = mock(InfluxDB.class);
    private final PersistOmiePricesRepository repository = new PersistOmiePricesRepositoryInflux(connectionManager);

    @Test
    void persistPrices_propagatesAFailedWrite() {
        when(connectionManager.getConnection()).thenReturn(connection);
        when(connectionManager.createBatchPoints()).thenReturn(BatchPoints.database("conluz").build());
        doThrow(new InfluxDBIOException(new IOException("connection refused"))).when(connection)
                .write(any(BatchPoints.class));

        List<PriceByHour> prices = List.of(
                new PriceByHour(0.1, OffsetDateTime.parse("2026-03-28T00:00:00+01:00")),
                new PriceByHour(0.2, OffsetDateTime.parse("2026-03-28T00:15:00+01:00")));

        assertThrows(InfluxDBIOException.class, () -> repository.persistPrices(prices));
        verify(connection).close();
    }
}
