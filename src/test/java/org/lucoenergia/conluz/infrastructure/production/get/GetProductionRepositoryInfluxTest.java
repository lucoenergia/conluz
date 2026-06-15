package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.infrastructure.production.EnergyProductionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GetProductionRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    private GetProductionRepositoryInflux repository;

    @Autowired
    private EnergyProductionInfluxLoader energyProductionInfluxLoader;

    @BeforeEach
    void beforeEach() {
        energyProductionInfluxLoader.loadData();
    }

    @AfterEach
    void afterEach() {
        energyProductionInfluxLoader.clearData();
    }


    @Test
    void testGetHourlyProductionByRangeOfDates() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");
        Float partitionCoefficient = 1.0f;

        List<ProductionByTime> result = repository.getHourlyProductionByRangeOfDates(startDate, endDate, partitionCoefficient);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // The loader loads 24 hourly data points for September 1, 2023
        assertEquals(24, result.size());

        // Verify first hour (00:00 - 01:00) - no production at night
        ProductionByTime hour1 = result.get(0);
        assertNotNull(hour1);
        assertNotNull(hour1.getPower());
        assertEquals(0.0d, hour1.getPower(), 0.01d, "First hour should have no production");
        assertNotNull(hour1.getTime());

        // Verify peak production hour (14:00 - 15:00 = hour 15)
        ProductionByTime peakHour = result.get(14);
        assertNotNull(peakHour);
        assertNotNull(peakHour.getPower());
        assertEquals(31.1d, peakHour.getPower(), 0.01d, "Peak hour should have 31.1 kW production");
        assertTrue(peakHour.getPower() > 0, "Peak hour should have positive production");

        // Verify hour 12 (11:00 - 12:00) - good solar production
        ProductionByTime hour12 = result.get(11);
        assertNotNull(hour12);
        assertEquals(25.76d, hour12.getPower(), 0.01d, "Hour 12 should have 25.76 kW production");

        // Verify last hour (23:00 - 00:00) - no production at night
        ProductionByTime lastHour = result.get(23);
        assertNotNull(lastHour);
        assertEquals(0.0d, lastHour.getPower(), 0.01d, "Last hour should have no production");

        // Verify total production is greater than 0
        double totalProduction = result.stream()
                .mapToDouble(ProductionByTime::getPower)
                .sum();
        assertTrue(totalProduction > 0, "Total production should be greater than 0");

        // Expected total: sum of all 24 hourly values
        // 0+0+0+0+0+0+0+0.13+1.32+5.45+15.97+25.76+27.79+25.29+31.1+26.87+30.95+28.86+10.48+5.37+0.81+0+0+0 = 236.15
        assertEquals(236.15d, totalProduction, 0.01d, "Total production should match sum of hourly values");
    }

    @Test
    void testGetHourlyProductionByRangeOfDatesWithPartitionCoefficient() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");
        Float partitionCoefficient = 0.5f; // 50% partition

        List<ProductionByTime> result = repository.getHourlyProductionByRangeOfDates(startDate, endDate, partitionCoefficient);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(24, result.size());

        // Verify peak hour with partition coefficient
        ProductionByTime peakHour = result.get(14);
        assertNotNull(peakHour);
        assertEquals(31.1d * 0.5d, peakHour.getPower(), 0.01d, "Production should be multiplied by partition coefficient");

        // Verify total production is half of the original
        double totalProduction = result.stream()
                .mapToDouble(ProductionByTime::getPower)
                .sum();
        assertEquals(236.15d * 0.5d, totalProduction, 0.01d, "Total production should be halved with 0.5 partition coefficient");
    }

    @Test
    void testGetMonthlyProductionByRangeOfDates() {
        // Date range covers 2023-09-01T00:00:00Z which is where the pre-aggregated monthly record is stored
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");
        Float partitionCoefficient = 1.0f;

        List<ProductionByTime> result = repository.getMonthlyProductionByRangeOfDates(startDate, endDate, partitionCoefficient);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        ProductionByTime monthData = result.get(0);
        assertNotNull(monthData);
        assertNotNull(monthData.getPower());
        assertNotNull(monthData.getTime());
        assertEquals(236.15d, monthData.getPower(), 0.01d, "Monthly production should match pre-aggregated value");
    }

    @Test
    void testGetMonthlyProductionByRangeOfDatesWithPartitionCoefficient() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");
        Float partitionCoefficient = 0.5f;

        List<ProductionByTime> result = repository.getMonthlyProductionByRangeOfDates(startDate, endDate, partitionCoefficient);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        ProductionByTime monthData = result.get(0);
        assertNotNull(monthData);
        assertEquals(236.15d * 0.5d, monthData.getPower(), 0.01d, "Monthly production should be multiplied by partition coefficient");
    }

    @Test
    void testGetYearlyProductionByRangeOfDates() {
        // Date range covers 2023-01-01T00:00:00Z where the pre-aggregated yearly record is stored
        OffsetDateTime startDate = OffsetDateTime.parse("2023-01-01T00:00:00.000+00:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-12-31T23:59:59.000+00:00");
        Float partitionCoefficient = 1.0f;

        List<ProductionByTime> result = repository.getYearlyProductionByRangeOfDates(startDate, endDate, partitionCoefficient);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        ProductionByTime yearData = result.get(0);
        assertNotNull(yearData);
        assertNotNull(yearData.getPower());
        assertNotNull(yearData.getTime());
        assertEquals(1000.0d, yearData.getPower(), 0.01d, "Yearly production should match pre-aggregated value");
    }

    @Test
    void testGetYearlyProductionByRangeOfDatesWithPartitionCoefficient() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-01-01T00:00:00.000+00:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-12-31T23:59:59.000+00:00");
        Float partitionCoefficient = 0.5f;

        List<ProductionByTime> result = repository.getYearlyProductionByRangeOfDates(startDate, endDate, partitionCoefficient);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        ProductionByTime yearData = result.get(0);
        assertNotNull(yearData);
        assertEquals(1000.0d * 0.5d, yearData.getPower(), 0.01d, "Yearly production should be multiplied by partition coefficient");
    }

    // --- Instant production ---

    @Test
    void testGetInstantProductionReturnsZeroForEmptyStationCodes() {
        InstantProduction result = repository.getInstantProduction(Collections.emptyList());

        assertNotNull(result);
        assertEquals(0.0d, result.getPower(), 0.01d, "Empty station codes should yield zero production");
    }

    @Test
    void testGetInstantProductionReturnsZeroForNullStationCodes() {
        InstantProduction result = repository.getInstantProduction(null);

        assertNotNull(result);
        assertEquals(0.0d, result.getPower(), 0.01d, "Null station codes should yield zero production");
    }

    @Test
    void testGetInstantProductionReturnsLastValueForSeededStation() {
        InstantProduction result = repository.getInstantProduction(List.of(EnergyProductionInfluxLoader.STATION_CODE));

        assertNotNull(result);
        // LAST() returns the most recent seeded hour (2023-09-01T23:00, a night-time hour with no production)
        assertEquals(0.0d, result.getPower(), 0.01d, "Instant production should be the last recorded value");
    }

    @Test
    void testGetInstantProductionReturnsZeroForUnknownStation() {
        InstantProduction result = repository.getInstantProduction(List.of("UNKNOWN_STATION"));

        assertNotNull(result);
        assertEquals(0.0d, result.getPower(), 0.01d, "Unknown station code should yield zero production");
    }

    // --- Daily production ---

    @Test
    void testGetDailyProductionByRangeOfDates() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");
        Float partitionCoefficient = 1.0f;

        List<ProductionByTime> result = repository.getDailyProductionByRangeOfDates(startDate, endDate, partitionCoefficient);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        double totalProduction = result.stream()
                .mapToDouble(ProductionByTime::getPower)
                .sum();
        assertEquals(236.15d, totalProduction, 0.01d, "Daily totals should sum to the full day's production");
    }

    @Test
    void testGetDailyProductionByRangeOfDatesWithPartitionCoefficient() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");
        Float partitionCoefficient = 0.5f;

        List<ProductionByTime> result = repository.getDailyProductionByRangeOfDates(startDate, endDate, partitionCoefficient);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        double totalProduction = result.stream()
                .mapToDouble(ProductionByTime::getPower)
                .sum();
        assertEquals(236.15d * 0.5d, totalProduction, 0.01d, "Daily totals should be halved with 0.5 partition coefficient");
    }

    // --- Station-code-scoped (community) variants ---

    @Test
    void testGetHourlyProductionByRangeOfDatesAndStationCodes() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getHourlyProductionByRangeOfDates(startDate, endDate, 1.0f,
                List.of(EnergyProductionInfluxLoader.STATION_CODE));

        assertNotNull(result);
        assertEquals(24, result.size());

        double totalProduction = result.stream()
                .mapToDouble(ProductionByTime::getPower)
                .sum();
        assertEquals(236.15d, totalProduction, 0.01d, "Scoped hourly production should match the seeded station's data");
    }

    @Test
    void testGetHourlyProductionByRangeOfDatesReturnsEmptyForEmptyStationCodes() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getHourlyProductionByRangeOfDates(startDate, endDate, 1.0f,
                Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Empty station codes should yield an empty list");
    }

    @Test
    void testGetHourlyProductionByRangeOfDatesReturnsEmptyForUnknownStation() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getHourlyProductionByRangeOfDates(startDate, endDate, 1.0f,
                List.of("UNKNOWN_STATION"));

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Unknown station code should yield an empty list");
    }

    @Test
    void testGetDailyProductionByRangeOfDatesAndStationCodes() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getDailyProductionByRangeOfDates(startDate, endDate, 1.0f,
                List.of(EnergyProductionInfluxLoader.STATION_CODE));

        assertNotNull(result);
        assertFalse(result.isEmpty());

        double totalProduction = result.stream()
                .mapToDouble(ProductionByTime::getPower)
                .sum();
        assertEquals(236.15d, totalProduction, 0.01d, "Scoped daily totals should match the seeded station's data");
    }

    @Test
    void testGetDailyProductionByRangeOfDatesReturnsEmptyForEmptyStationCodes() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getDailyProductionByRangeOfDates(startDate, endDate, 1.0f,
                Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Empty station codes should yield an empty list");
    }

    @Test
    void testGetMonthlyProductionByRangeOfDatesAndStationCodes() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getMonthlyProductionByRangeOfDates(startDate, endDate, 1.0f,
                List.of(EnergyProductionInfluxLoader.STATION_CODE));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(236.15d, result.get(0).getPower(), 0.01d, "Scoped monthly production should match pre-aggregated value");
    }

    @Test
    void testGetMonthlyProductionByRangeOfDatesReturnsEmptyForEmptyStationCodes() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getMonthlyProductionByRangeOfDates(startDate, endDate, 1.0f,
                Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Empty station codes should yield an empty list");
    }

    @Test
    void testGetYearlyProductionByRangeOfDatesAndStationCodes() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-01-01T00:00:00.000+00:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-12-31T23:59:59.000+00:00");

        List<ProductionByTime> result = repository.getYearlyProductionByRangeOfDates(startDate, endDate, 1.0f,
                List.of(EnergyProductionInfluxLoader.STATION_CODE));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1000.0d, result.get(0).getPower(), 0.01d, "Scoped yearly production should match pre-aggregated value");
    }

    @Test
    void testGetYearlyProductionByRangeOfDatesReturnsEmptyForEmptyStationCodes() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-01-01T00:00:00.000+00:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-12-31T23:59:59.000+00:00");

        List<ProductionByTime> result = repository.getYearlyProductionByRangeOfDates(startDate, endDate, 1.0f,
                Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Empty station codes should yield an empty list");
    }
}
