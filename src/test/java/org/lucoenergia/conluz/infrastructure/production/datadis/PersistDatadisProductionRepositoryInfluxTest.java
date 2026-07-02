package org.lucoenergia.conluz.infrastructure.production.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.PersistDatadisProductionRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration test for {@link PersistDatadisProductionRepositoryInflux}. It writes a production
 * point and reads it back from InfluxDB to assert the persisted cups, production_kwh, obtain_method
 * and timestamp. April 1, 2023 10:00 Europe/Madrid (CEST, UTC+2) equals 2023-04-01T08:00:00Z.
 */
@SpringBootTest
class PersistDatadisProductionRepositoryInfluxTest extends BaseIntegrationTest {

    private static final String CUPS_CODE = "ES0031406912345678JN0F";

    @Autowired
    private PersistDatadisProductionRepository repository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    @AfterEach
    void tearDown() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT, CUPS_CODE)));
        }
    }

    @Test
    void testPersistHourlyProductionsWritesReadableData() {

        DatadisProduction production = new DatadisProduction();
        production.setCups(CUPS_CODE);
        production.setDate("2023/04/01");
        production.setTime("10:00");
        production.setProductionKWh(1.93f);
        production.setObtainMethod("Real");

        repository.persistHourlyProductions(List.of(production));

        List<DatadisProductionPoint> result = queryProductionData(
                "2023-04-01T00:00:00Z", "2023-04-02T00:00:00Z");

        assertFalse(result.isEmpty(), "Expected the production point to be persisted");
        assertEquals(1, result.size());

        DatadisProductionPoint point = result.get(0);
        assertEquals(CUPS_CODE, point.getCups());
        assertEquals(1.93, point.getProductionKWh(), 0.001);
        assertEquals("Real", point.getObtainMethod());
        assertEquals(Instant.parse("2023-04-01T08:00:00Z"), point.getTime());
    }

    private List<DatadisProductionPoint> queryProductionData(String startDate, String endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT, CUPS_CODE, startDate, endDate));
            QueryResult result = connection.query(query);
            InfluxDBResultMapper mapper = new InfluxDBResultMapper();
            return mapper.toPOJO(result, DatadisProductionPoint.class);
        }
    }
}
