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
}

