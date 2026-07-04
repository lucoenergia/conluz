package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GetDatadisProductionRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    @Qualifier("getDatadisProductionRepositoryInflux")
    private GetDatadisProductionRepositoryInflux repository;

    @Autowired
    private DatadisProductionInfluxLoader datadisProductionInfluxLoader;

    private static final String CUPS_A = DatadisProductionInfluxLoader.CUPS_CODE_A;
    private static final String CUPS_B = DatadisProductionInfluxLoader.CUPS_CODE_B;

    @BeforeEach
    void beforeEach() {
        datadisProductionInfluxLoader.loadData();
    }

    @AfterEach
    void afterEach() {
        datadisProductionInfluxLoader.clearData();
    }

    @Test
    void testGetHourlyProductionByRangeOfDates() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        List<DatadisProduction> result = repository.getHourlyProductionByRangeOfDates(List.of(CUPS_A), startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        // GROUP BY time(1h) fills every hour of the 30-day range: 30 * 24 = 720 buckets.
        assertEquals(720, result.size());

        // 2023-04-01T00:00:00Z → Europe/Madrid 02:00 (UTC+2, CEST).
        DatadisProduction hour0 = result.get(0);
        assertEquals(CUPS_A, hour0.getCups());
        assertEquals("2023/04/01", hour0.getDate());
        assertEquals("02:00", hour0.getTime());
        assertEquals(0.5f, hour0.getProductionKWh(), 0.001f);
        assertEquals("Real", hour0.getObtainMethod());

        assertEquals(0.5f, result.get(1).getProductionKWh(), 0.001f);   // 01:00Z
        assertEquals(1.0f, result.get(22).getProductionKWh(), 0.001f);  // 22:00Z
        assertEquals(2.0f, result.get(23).getProductionKWh(), 0.001f);  // 23:00Z
        assertEquals(3.0f, result.get(24).getProductionKWh(), 0.001f);  // next day 00:00Z
        assertEquals(1.0f, result.get(25).getProductionKWh(), 0.001f);  // next day 01:00Z

        // Empty hours are still returned, aggregated to 0.0.
        assertEquals(0.0f, result.get(2).getProductionKWh(), 0.001f);
    }

    @Test
    void testGetDailyProductionByRangeOfDatesBucketsByDayAcrossBoundary() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        List<DatadisProduction> result = repository.getDailyProductionByRangeOfDates(List.of(CUPS_A), startDate, endDate);

        assertNotNull(result);
        // GROUP BY time(1d) over a 30-day range yields 30 buckets.
        assertEquals(30, result.size());

        // April 1 bucket = 0.5 + 0.5 + 1.0 + 2.0 = 4.0 (includes the 22:00Z and 23:00Z points).
        DatadisProduction day1 = result.get(0);
        assertEquals(CUPS_A, day1.getCups());
        assertEquals("2023/04/01", day1.getDate());
        assertEquals(4.0f, day1.getProductionKWh(), 0.001f);
        assertEquals("Real", day1.getObtainMethod());

        // April 2 bucket = 3.0 + 1.0 = 4.0. That the 23:00Z point stayed in day 1 and the 00:00Z point
        // moved to day 2 proves the daily buckets split on the (UTC) day boundary.
        DatadisProduction day2 = result.get(1);
        assertEquals("2023/04/02", day2.getDate());
        assertEquals(4.0f, day2.getProductionKWh(), 0.001f);

        // Remaining days are empty but present.
        assertEquals(0.0f, result.get(2).getProductionKWh(), 0.001f);
    }

    @Test
    void testGetMonthlyProductionByRangeOfDates() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        List<DatadisProduction> result = repository.getMonthlyProductionByRangeOfDates(List.of(CUPS_A), startDate, endDate);

        assertNotNull(result);
        assertEquals(1, result.size());

        DatadisProduction month = result.get(0);
        assertEquals(CUPS_A, month.getCups());
        assertEquals("2023/04/01", month.getDate());
        assertEquals(100.0f, month.getProductionKWh(), 0.001f);
        assertEquals("Real", month.getObtainMethod());
    }

    @Test
    void testGetYearlyProductionByRangeOfDates() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-01-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-12-31T23:59:59Z");

        List<DatadisProduction> result = repository.getYearlyProductionByRangeOfDates(List.of(CUPS_A), startDate, endDate);

        assertNotNull(result);
        assertEquals(1, result.size());

        DatadisProduction year = result.get(0);
        assertEquals(CUPS_A, year.getCups());
        assertEquals(1200.0f, year.getProductionKWh(), 0.001f);
        assertEquals("Real", year.getObtainMethod());
    }

    @Test
    void testMultipleCupsAreReturnedAndKeptSeparate() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        List<DatadisProduction> result = repository.getMonthlyProductionByRangeOfDates(List.of(CUPS_A, CUPS_B), startDate, endDate);

        assertNotNull(result);
        assertEquals(2, result.size());

        DatadisProduction a = findByCups(result, CUPS_A);
        assertEquals(100.0f, a.getProductionKWh(), 0.001f);
        assertEquals("Real", a.getObtainMethod());

        DatadisProduction b = findByCups(result, CUPS_B);
        assertEquals(50.0f, b.getProductionKWh(), 0.001f);
        assertEquals("Estimated", b.getObtainMethod());
    }

    private DatadisProduction findByCups(List<DatadisProduction> result, String cups) {
        Optional<DatadisProduction> match = result.stream()
                .filter(p -> cups.equals(p.getCups()))
                .findFirst();
        assertTrue(match.isPresent(), "Expected a production record for cups " + cups);
        return match.get();
    }
}
