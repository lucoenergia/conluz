package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves things about the InfluxQL this repository builds that an integration test against the
 * single configured zone could not show: that the daily query takes its {@code tz()} clause from
 * {@link ZoneResolver} rather than hardcoding a zone, that the hourly query carries no {@code tz()}
 * clause at all -- hourly consumption is out of scope for local-calendar alignment, so its
 * statement must stay exactly what it was -- and that both grouped queries select every energy
 * field the mapper reads.
 *
 * <p>A plain Mockito test (no Spring context) so a zone other than {@code conluz.time.zone.id}
 * (Europe/Madrid in {@code application-test.properties}) can be exercised without a second
 * Testcontainers InfluxDB.
 */
class GetDatadisConsumptionRepositoryInfluxTimeZoneTest {

    private static final ZoneId ZONE_UNDER_TEST = ZoneId.of("America/New_York");

    private final InfluxDB influxDB = mock(InfluxDB.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final Supply supply = SupplyMother.random().withCode("ES0031406912345678JN0F").build();

    private final GetDatadisConsumptionRepositoryInflux repository = buildRepository();

    private GetDatadisConsumptionRepositoryInflux buildRepository() {
        InfluxDbConnectionManager connectionManager = mock(InfluxDbConnectionManager.class);
        QueryResult emptyResult = new QueryResult();
        emptyResult.setResults(new ArrayList<>());
        when(connectionManager.getConnection()).thenReturn(influxDB);
        when(influxDB.query(any(Query.class))).thenReturn(emptyResult);

        TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);
        return new GetDatadisConsumptionRepositoryInflux(
                connectionManager, new DateConverter(timeConfiguration), zoneResolver);
    }

    @Test
    void dailyConsumptionUsesTheZoneResolverZoneNotAHardcodedOne() {
        when(zoneResolver.resolveZoneIdForSupply(supply.getId())).thenReturn(ZONE_UNDER_TEST);

        repository.getDailyConsumptionsByRangeOfDates(supply,
                OffsetDateTime.parse("2023-04-01T00:00:00Z"), OffsetDateTime.parse("2023-04-30T23:59:59Z"));

        String command = capturedCommand();
        assertTrue(command.contains("tz('America/New_York')"),
                "Expected the daily query to use the zone ZoneResolver returned: " + command);
        assertFalse(command.contains("Europe/Madrid"),
                "The daily query must not hardcode a zone: " + command);
    }

    @Test
    void hourlyConsumptionCarriesNoTimeZoneClause() {
        repository.getHourlyConsumptionsByRangeOfDates(supply,
                OffsetDateTime.parse("2023-04-01T00:00:00Z"), OffsetDateTime.parse("2023-04-30T23:59:59Z"));

        String command = capturedCommand();
        assertFalse(command.contains("tz("),
                "Hourly consumption is out of scope for local-calendar alignment and must emit no tz() clause: "
                        + command);
        assertTrue(command.contains("GROUP BY time(1h), cups"),
                "Expected the hourly grouping to be unchanged: " + command);
    }

    /**
     * An energy field missing from the SELECT list does not fail: it comes back null and is
     * flattened to {@code 0.0}, which reads as "this supply generated nothing" rather than as a
     * bug. {@code generation_energy_kwh} was omitted for exactly that reason, so the column list is
     * pinned here as well as through the values the integration tests read back.
     */
    @Test
    void bothGroupedQueriesSumEveryEnergyField() {
        when(zoneResolver.resolveZoneIdForSupply(supply.getId())).thenReturn(ZONE_UNDER_TEST);
        OffsetDateTime start = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        repository.getDailyConsumptionsByRangeOfDates(supply, start, end);
        repository.getHourlyConsumptionsByRangeOfDates(supply, start, end);

        for (Query query : capturedCommands()) {
            String command = query.getCommand();
            for (String field : List.of("consumption_kwh", "surplus_energy_kwh",
                    "generation_energy_kwh", "self_consumption_energy_kwh")) {
                assertTrue(command.contains(String.format("SUM(\"%s\")", field)),
                        () -> "Expected the grouped query to sum " + field + ": " + command);
            }
        }
    }

    private List<Query> capturedCommands() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(influxDB, times(2)).query(captor.capture());
        return captor.getAllValues();
    }

    private String capturedCommand() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(influxDB).query(captor.capture());
        return captor.getValue().getCommand();
    }
}
