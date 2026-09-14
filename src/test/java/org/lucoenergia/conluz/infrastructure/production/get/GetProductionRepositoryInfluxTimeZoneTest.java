package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that {@link GetProductionRepositoryInflux#getLocalCalendarDailyProductionHalfOpen} builds
 * its InfluxQL {@code tz()} clause from the application's configured timezone
 * ({@link TimeConfiguration#getZoneId()}), not a hardcoded one. A plain Mockito unit test (no Spring
 * context) so it can exercise a zone other than whatever {@code conluz.time.zone.id} is set to in
 * {@code application-test.properties} (currently Europe/Madrid, exercised instead by
 * {@link GetProductionRepositoryInfluxTest}'s DST-alignment test) without needing a second
 * Testcontainers InfluxDB configured for a different zone.
 */
class GetProductionRepositoryInfluxTimeZoneTest {

    @Test
    void getLocalCalendarDailyProductionHalfOpenUsesTheConfiguredTimeZoneNotAHardcodedOne() {
        InfluxDbConnectionManager connectionManager = mock(InfluxDbConnectionManager.class);
        InfluxDB influxDB = mock(InfluxDB.class);
        QueryResult emptyResult = new QueryResult();
        emptyResult.setResults(new ArrayList<>());
        when(connectionManager.getConnection()).thenReturn(influxDB);
        when(influxDB.query(any(Query.class))).thenReturn(emptyResult);

        TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("America/New_York"));
        DateConverter dateConverter = new DateConverter(timeConfiguration);

        GetProductionRepositoryInflux repository = new GetProductionRepositoryInflux(
                connectionManager, dateConverter, timeConfiguration,
                new InstantProductionInfluxMapper(), new ProductionByHourInfluxMapper(dateConverter));

        OffsetDateTime from = OffsetDateTime.parse("2023-09-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2023-09-03T00:00:00Z");

        repository.getLocalCalendarDailyProductionHalfOpen(from, to, List.of("PLANT001"));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(influxDB).query(captor.capture());
        String command = captor.getValue().getCommand();

        assertTrue(command.contains("tz('America/New_York')"),
                "Expected the query to use the configured zone, not a hardcoded one: " + command);
        assertFalse(command.contains("Europe/Madrid"),
                "Query must not hardcode Europe/Madrid: " + command);
    }
}
