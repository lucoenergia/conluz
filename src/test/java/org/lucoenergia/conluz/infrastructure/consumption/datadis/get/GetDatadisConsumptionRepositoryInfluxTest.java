package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxLoader;
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
class GetDatadisConsumptionRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    @Qualifier("getDatadisConsumptionRepositoryInflux")
    private GetDatadisConsumptionRepositoryInflux repository;

    @Autowired
    private DatadisConsumptionInfluxLoader datadisConsumptionInfluxLoader;

    @BeforeEach
    void beforeEach() {
        datadisConsumptionInfluxLoader.loadData();
    }

    @AfterEach
    void afterEach() {
        datadisConsumptionInfluxLoader.clearData();
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

        // The loader loads 30 hourly data points (24 hours for April 1 + 6 hours for April 2)
        // When grouped by day, we expect 2 daily aggregated results, the rest of the 28 days will be empty but returned
        assertEquals(30, result.size());

        // Verify first day (April 1, 2023) - 24 hours of data
        DatadisConsumption day1 = result.get(0);
        assertNotNull(day1);
        assertEquals("ES0031406912345678JN0F", day1.getCups());

        // Verify date and time
        // First timestamp: 1680307200000000000L = 2023-04-01T00:00:00Z
        // In Europe/Madrid timezone (UTC+2 in April - CEST), this becomes 2023-04-01T02:00:00
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
        // Sum = 0.45+0.42+0.38+0.35+0.33+0.32+0.40+0.55+0.68+0.72+0.75+0.78+0.80+0.76+0.70+0.65+0.58+0.50+0.55+0.60+0.58+0.52+0.48+0.46 = 13.31
        assertEquals(13.31f, day1.getConsumptionKWh(), 0.01f, "Day 1 total consumption should match sum of hourly values");

        // Expected total surplus for April 1: sum of all 24 hourly values
        // Sum = 0.0+0.0+0.0+0.0+0.0+0.0+0.0+0.0+0.10+0.20+0.25+0.30+0.35+0.28+0.22+0.15+0.08+0.0+0.0+0.0+0.0+0.0+0.0+0.0 = 1.93
        assertEquals(1.93f, day1.getSurplusEnergyKWh(), 0.01f, "Day 1 total surplus should match sum of hourly values");

        // Expected total self-consumption for April 1: sum of all 24 hourly values
        // Sum = 0.0+0.0+0.0+0.0+0.0+0.0+0.0+0.0+0.15+0.25+0.30+0.35+0.40+0.33+0.27+0.20+0.12+0.0+0.0+0.0+0.0+0.0+0.0+0.0 = 2.37
        assertEquals(2.37f, day1.getSelfConsumptionEnergyKWh(), 0.01f, "Day 1 total self-consumption should match sum of hourly values");

        // Verify second day (April 2, 2023) - 6 hours of data
        DatadisConsumption day2 = result.get(1);
        assertNotNull(day2);
        assertEquals("ES0031406912345678JN0F", day2.getCups());

        // Verify date and time
        // First timestamp of day 2: 1680393600000000000L = 2023-04-02T00:00:00Z
        // In Europe/Madrid timezone (UTC+2), this becomes 2023-04-02T02:00:00
        assertNotNull(day2.getDate());
        assertEquals("2023/04/02", day2.getDate(), "Date should be April 2, 2023 in Europe/Madrid timezone");
        assertNotNull(day2.getTime());
        assertEquals("02:00", day2.getTime(), "Time should be 02:00 in Europe/Madrid timezone (UTC+2)");

        assertNotNull(day2.getConsumptionKWh());
        assertTrue(day2.getConsumptionKWh() > 0, "Consumption should be greater than 0");

        // Verify null-safe handling: values should never be null, parseToFloat returns 0.0f for nulls
        assertNotNull(day2.getSurplusEnergyKWh());
        assertNotNull(day2.getSelfConsumptionEnergyKWh());

        // Expected total consumption for April 2: sum of 6 hourly values
        // Sum = 0.44+0.40+0.37+0.36+0.34+0.35 = 2.26
        assertEquals(2.26f, day2.getConsumptionKWh(), 0.01f, "Day 2 total consumption should match sum of hourly values");

        // April 2 has no surplus or self-consumption in the test data
        assertEquals(0.0f, day2.getSurplusEnergyKWh(), 0.01f, "Day 2 should have no surplus energy");
        assertEquals(0.0f, day2.getSelfConsumptionEnergyKWh(), 0.01f, "Day 2 should have no self-consumption energy");
    }

    @Test
    void testGetHourlyConsumptionsByRangeOfDates() {
        User user = UserMother.randomUser();
        Supply supply = SupplyMother.random(user)
                .withCode("ES0031406912345678JN0F")
                .build();

        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        List<DatadisConsumption> result = repository.getHourlyConsumptionsByRangeOfDates(supply, startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // The loader loads 30 hourly data points (24 hours for April 1 + 6 hours for April 2)
        // When grouped by hour, InfluxDB fills all hours in the range (30 days * 24 hours = 720)
        assertEquals(720, result.size());

        // Verify first hour (April 1, 2023 00:00:00 UTC)
        DatadisConsumption hour1 = result.get(0);
        assertNotNull(hour1);
        assertEquals("ES0031406912345678JN0F", hour1.getCups());

        // Verify date and time
        // First timestamp: 1680307200000000000L = 2023-04-01T00:00:00Z
        // In Europe/Madrid timezone (UTC+2 in April - CEST), this becomes 2023-04-01T02:00:00
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

        // Verify first hour of April 2
        DatadisConsumption day2Hour1 = result.get(24);
        assertNotNull(day2Hour1);
        assertEquals("ES0031406912345678JN0F", day2Hour1.getCups());
        assertEquals("2023/04/02", day2Hour1.getDate(), "Date should be April 2, 2023");
        assertEquals(0.44f, day2Hour1.getConsumptionKWh(), 0.01f, "First hour of April 2 consumption should be 0.44 kWh");
    }

    @Test
    void testGetMonthlyConsumptionsByRangeOfDates() {
        User user = UserMother.randomUser();
        Supply supply = SupplyMother.random(user)
                .withCode("ES0031406912345678JN0F")
                .build();

        OffsetDateTime startDate = OffsetDateTime.parse("2023-04-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-04-30T23:59:59Z");

        List<DatadisConsumption> result = repository.getMonthlyConsumptionsByRangeOfDates(supply, startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // The loader now populates monthly aggregated data
        // Query for April 2023 returns 1 monthly aggregated result
        assertEquals(1, result.size());

        // Verify April 2023 monthly data
        DatadisConsumption monthData = result.get(0);
        assertNotNull(monthData);
        assertEquals("ES0031406912345678JN0F", monthData.getCups());

        // Verify date and time are present
        // April 2023 monthly data timestamp is April 1, 2023 00:00:00 UTC
        // In Europe/Madrid timezone (UTC+2 in April), this becomes 2023-04-01T02:00:00
        assertNotNull(monthData.getDate());
        assertEquals("2023/04/01", monthData.getDate(), "Date should be April 1, 2023 in Europe/Madrid timezone");
        assertNotNull(monthData.getTime());
        assertEquals("02:00", monthData.getTime(), "Time should be 02:00 in Europe/Madrid timezone (UTC+2)");

        assertNotNull(monthData.getConsumptionKWh());
        assertTrue(monthData.getConsumptionKWh() > 0, "Consumption should be greater than 0");

        // Verify surplus and self-consumption energy are not null and >= 0
        assertNotNull(monthData.getSurplusEnergyKWh());
        assertTrue(monthData.getSurplusEnergyKWh() >= 0, "Surplus energy should be >= 0");
        assertNotNull(monthData.getSelfConsumptionEnergyKWh());
        assertTrue(monthData.getSelfConsumptionEnergyKWh() >= 0, "Self-consumption energy should be >= 0");

        // Expected values for April 2023 from CONSUMPTION_BY_MONTH:
        // April 2023: 330.2 kWh consumption, 65.0 kWh surplus, 135.0 kWh self-consumption
        assertEquals(330.2f, monthData.getConsumptionKWh(), 0.01f, "April 2023 consumption should match monthly aggregated value");
        assertEquals(65.0f, monthData.getSurplusEnergyKWh(), 0.01f, "April 2023 surplus should match monthly aggregated value");
        assertEquals(135.0f, monthData.getSelfConsumptionEnergyKWh(), 0.01f, "April 2023 self-consumption should match monthly aggregated value");
    }

    @Test
    void testGetYearlyConsumptionsByRangeOfDates() {
        User user = UserMother.randomUser();
        Supply supply = SupplyMother.random(user)
                .withCode("ES0031406912345678JN0F")
                .build();

        OffsetDateTime startDate = OffsetDateTime.parse("2023-01-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-12-31T23:59:59Z");

        List<DatadisConsumption> result = repository.getYearlyConsumptionsByRangeOfDates(supply, startDate, endDate);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // The loader now populates yearly aggregated data
        // Query for 2023 returns 1 yearly aggregated result
        assertEquals(1, result.size());

        // Verify 2023 yearly data
        DatadisConsumption yearData = result.get(0);
        assertNotNull(yearData);
        assertEquals("ES0031406912345678JN0F", yearData.getCups());

        // Verify date and time are present
        // 2023 yearly data timestamp is January 1, 2023 00:00:00 UTC
        // In Europe/Madrid timezone (UTC+1 in January - CET), this becomes 2023-01-01T01:00:00
        assertNotNull(yearData.getDate());
        assertEquals("2023/01/01", yearData.getDate(), "Date should be January 1, 2023 in Europe/Madrid timezone");
        assertNotNull(yearData.getTime());
        assertEquals("01:00", yearData.getTime(), "Time should be 01:00 in Europe/Madrid timezone (UTC+1)");

        assertNotNull(yearData.getConsumptionKWh());
        assertTrue(yearData.getConsumptionKWh() > 0, "Consumption should be greater than 0");

        // Verify surplus and self-consumption energy are not null and >= 0
        assertNotNull(yearData.getSurplusEnergyKWh());
        assertTrue(yearData.getSurplusEnergyKWh() >= 0, "Surplus energy should be >= 0");
        assertNotNull(yearData.getSelfConsumptionEnergyKWh());
        assertTrue(yearData.getSelfConsumptionEnergyKWh() >= 0, "Self-consumption energy should be >= 0");

        // Expected values for 2023 from CONSUMPTION_BY_YEAR:
        // 2023: 4205.3 kWh consumption, 726.0 kWh surplus, 1453.0 kWh self-consumption
        assertEquals(4205.3f, yearData.getConsumptionKWh(), 0.01f, "2023 consumption should match yearly aggregated value");
        assertEquals(726.0f, yearData.getSurplusEnergyKWh(), 0.01f, "2023 surplus should match yearly aggregated value");
        assertEquals(1453.0f, yearData.getSelfConsumptionEnergyKWh(), 0.01f, "2023 self-consumption should match yearly aggregated value");
    }
}

