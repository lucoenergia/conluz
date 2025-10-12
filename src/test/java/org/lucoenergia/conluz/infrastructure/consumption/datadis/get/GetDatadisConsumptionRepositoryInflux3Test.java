package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInflux3Loader;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Month;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GetDatadisConsumptionRepositoryInflux3Test extends BaseIntegrationTest {

    @Autowired
    @Qualifier("getDatadisConsumptionRepositoryInflux3")
    private GetDatadisConsumptionRepositoryInflux3 repository;

    @Autowired
    private DatadisConsumptionInflux3Loader datadisConsumptionInflux3Loader;

    @BeforeEach
    void beforeEach() {
        datadisConsumptionInflux3Loader.loadData();
    }

    @AfterEach
    void afterEach() {
        datadisConsumptionInflux3Loader.clearData();
    }

    @Test
    void testGetHourlyConsumptionsByMonth() {
        User user = UserMother.randomUser();
        Supply supply = SupplyMother.random(user)
                .withCode("ES0031406912345678JN0F")
                .build();

        List<DatadisConsumption> result = repository.getHourlyConsumptionsByMonth(supply, Month.APRIL, 2023);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify first record
        DatadisConsumption first = result.get(0);
        assertNotNull(first);
        assertEquals("ES0031406912345678JN0F", first.getCups());
        assertNotNull(first.getDate());
        assertNotNull(first.getTime());
        assertNotNull(first.getConsumptionKWh());
    }

    @Test
    void testGetDailyConsumptionsByRangeOfDates() {
        User user = UserMother.randomUser();
        Supply supply = SupplyMother.random(user)
                .withCode("ES0031406912345678JN0F")
                .build();

        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        List<DatadisConsumption> result = repository.getDailyConsumptionsByRangeOfDates(supply, startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify first day (April 1, 2023) - 24 hours of data
        DatadisConsumption day1 = result.get(0);
        assertNotNull(day1);
        assertEquals("ES0031406912345678JN0F", day1.getCups());

        // Verify date and time
        assertNotNull(day1.getDate());
        assertEquals("2023/04/01", day1.getDate(), "Date should be April 1, 2023 in Europe/Madrid timezone");
        assertNotNull(day1.getTime());
        assertEquals("02:00", day1.getTime(), "Time should be 02:00 in Europe/Madrid timezone (UTC+2)");

        assertNotNull(day1.getConsumptionKWh());
        assertTrue(day1.getConsumptionKWh() > 0, "Consumption should be greater than 0");

        // Verify surplus and self-consumption energy are not null and >= 0
        assertNotNull(day1.getSurplusEnergyKWh());
        assertTrue(day1.getSurplusEnergyKWh() >= 0, "Surplus energy should be >= 0");
        assertNotNull(day1.getSelfConsumptionEnergyKWh());
        assertTrue(day1.getSelfConsumptionEnergyKWh() >= 0, "Self-consumption energy should be >= 0");

        // Expected total consumption for April 1: sum of all 24 hourly values
        assertEquals(13.31f, day1.getConsumptionKWh(), 0.01f, "Day 1 total consumption should match sum of hourly values");

        // Expected total surplus for April 1: sum of all 24 hourly values
        assertEquals(1.93f, day1.getSurplusEnergyKWh(), 0.01f, "Day 1 total surplus should match sum of hourly values");

        // Expected total self-consumption for April 1: sum of all 24 hourly values
        assertEquals(2.37f, day1.getSelfConsumptionEnergyKWh(), 0.01f, "Day 1 total self-consumption should match sum of hourly values");
    }

    @Test
    void testGetHourlyConsumptionsByRangeOfDates() {
        User user = UserMother.randomUser();
        Supply supply = SupplyMother.random(user)
                .withCode("ES0031406912345678JN0F")
                .build();

        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-02T06:00:00Z");

        List<DatadisConsumption> result = repository.getHourlyConsumptionsByRangeOfDates(supply, startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify first hour (April 1, 2023 00:00:00 UTC)
        DatadisConsumption hour1 = result.get(0);
        assertNotNull(hour1);
        assertEquals("ES0031406912345678JN0F", hour1.getCups());

        // Verify date and time
        assertNotNull(hour1.getDate());
        assertEquals("2023/04/01", hour1.getDate(), "Date should be April 1, 2023 in Europe/Madrid timezone");
        assertNotNull(hour1.getTime());
        assertEquals("02:00", hour1.getTime(), "Time should be 02:00 in Europe/Madrid timezone (UTC+2)");

        assertNotNull(hour1.getConsumptionKWh());
        assertEquals(0.45f, hour1.getConsumptionKWh(), 0.01f, "First hour consumption should be 0.45 kWh");

        // Verify surplus and self-consumption energy
        assertNotNull(hour1.getSurplusEnergyKWh());
        assertEquals(0.0f, hour1.getSurplusEnergyKWh(), 0.01f, "First hour should have no surplus energy");
        assertNotNull(hour1.getSelfConsumptionEnergyKWh());
        assertEquals(0.0f, hour1.getSelfConsumptionEnergyKWh(), 0.01f, "First hour should have no self-consumption energy");

        // Verify an hour with solar production (9th hour = 08:00 UTC = 10:00 CEST)
        DatadisConsumption hour9 = result.get(8);
        assertNotNull(hour9);
        assertEquals("ES0031406912345678JN0F", hour9.getCups());
        assertEquals(0.68f, hour9.getConsumptionKWh(), 0.01f, "9th hour consumption should be 0.68 kWh");
        assertEquals(0.10f, hour9.getSurplusEnergyKWh(), 0.01f, "9th hour should have 0.10 kWh surplus energy");
        assertEquals(0.15f, hour9.getSelfConsumptionEnergyKWh(), 0.01f, "9th hour should have 0.15 kWh self-consumption energy");

        // Verify last hour of April 1 (24th hour)
        DatadisConsumption hour24 = result.get(23);
        assertNotNull(hour24);
        assertEquals("ES0031406912345678JN0F", hour24.getCups());
        assertEquals(0.46f, hour24.getConsumptionKWh(), 0.01f, "24th hour consumption should be 0.46 kWh");
        assertEquals(0.0f, hour24.getSurplusEnergyKWh(), 0.01f, "24th hour should have no surplus energy");
        assertEquals(0.0f, hour24.getSelfConsumptionEnergyKWh(), 0.01f, "24th hour should have no self-consumption energy");
    }

    @Test
    void testDateTruncFunctionality() {
        // This test verifies the main benefit of InfluxDB 3: calendar-based aggregation using DATE_TRUNC
        User user = UserMother.randomUser();
        Supply supply = SupplyMother.random(user)
                .withCode("ES0031406912345678JN0F")
                .build();

        // Test daily aggregation with DATE_TRUNC('day', time)
        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        List<DatadisConsumption> dailyResult = repository.getDailyConsumptionsByRangeOfDates(supply, startDate, endDate);

        assertNotNull(dailyResult);
        assertFalse(dailyResult.isEmpty());

        // Verify that we get daily aggregated data
        // Each record should represent a full day's consumption
        assertTrue(dailyResult.size() >= 2, "Should have at least 2 days of data");

        // Verify first day total is the sum of all 24 hours
        DatadisConsumption firstDay = dailyResult.get(0);
        assertTrue(firstDay.getConsumptionKWh() > 10.0f, "Daily consumption should be sum of hourly values (> 10 kWh)");
    }
}
