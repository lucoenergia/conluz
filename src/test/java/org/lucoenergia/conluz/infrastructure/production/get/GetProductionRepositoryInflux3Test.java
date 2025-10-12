package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.infrastructure.production.EnergyProductionInflux3Loader;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GetProductionRepositoryInflux3Test extends BaseIntegrationTest {

    @Autowired
    @Qualifier("getProductionRepositoryInflux3")
    private GetProductionRepositoryInflux3 repository;

    @Autowired
    private EnergyProductionInflux3Loader energyProductionInflux3Loader;

    @BeforeEach
    void beforeEach() {
        energyProductionInflux3Loader.loadData();
    }

    @AfterEach
    void afterEach() {
        energyProductionInflux3Loader.clearData();
    }

    @Test
    void testGetInstantProduction() {

        Double result = repository.getInstantProduction().getPower();

        Assertions.assertNotNull(result);
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
    void testGetDailyProductionByRangeOfDates() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-03T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getDailyProductionByRangeOfDates(startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Should return daily aggregated data
        assertTrue(result.size() <= 3, "Should have at most 3 days of data");
    }

    @Test
    void testGetMonthlyProductionByRangeOfDates() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+00:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-30T23:00:00.000+00:00");

        List<ProductionByTime> result = repository.getMonthlyProductionByRangeOfDates(startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Should return monthly aggregated data (1 month)
        assertEquals(1, result.size(), "Should have 1 month of data");
    }

    @Test
    void testGetYearlyProductionByRangeOfDates() {
        OffsetDateTime startDate = OffsetDateTime.parse("2023-01-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-12-31T23:00:00.000+02:00");

        List<ProductionByTime> result = repository.getYearlyProductionByRangeOfDates(startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Should return yearly aggregated data (1 year)
        assertEquals(1, result.size(), "Should have 1 year of data");
    }
}
