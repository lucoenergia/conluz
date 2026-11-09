package org.lucoenergia.conluz.infrastructure.price;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryInflux;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GetPriceRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    private GetPriceRepositoryInflux repository;
    @Autowired
    private EnergyPricesInfluxLoader energyPricesInfluxLoader;
    @Autowired
    private GetPriceRepository getPriceRepository;
    @Autowired
    private PersistOmiePricesRepository persistOmiePricesRepository;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    /** A window no other test writes prices to: prices are not kept per test, so it is deleted whole. */
    private static final Instant WINDOW_FROM = Instant.parse("2025-11-01T00:00:00Z");
    private static final Instant WINDOW_TO = Instant.parse("2025-11-05T00:00:00Z");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** 12:00 on 3 November 2025 in Madrid. */
    private static final Instant NOON = Instant.parse("2025-11-03T11:00:00Z");

    @BeforeEach
    void beforeEach() {
        energyPricesInfluxLoader.loadData();
    }

    @BeforeEach
    void afterEach() {
        energyPricesInfluxLoader.clearData();
    }

    @Test
    void testGetPriceByRangeOfDates() {

        OffsetDateTime startDate = OffsetDateTime.parse("2023-10-25T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-10-25T23:00:00.000+02:00");

        List<PriceByHour> result = repository.getPricesByRangeOfDates(startDate, endDate);

        Assertions.assertEquals(24, result.size());
    }

    @AfterEach
    void deleteThePricesOfTheWindow() {
        // No port deletes prices: production never does. The price sync tests delete theirs the same way.
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format("DELETE FROM \"%s\" WHERE time >= '%s' AND time < '%s'",
                    OmieConfig.PRICES_KWH_MEASUREMENT, WINDOW_FROM, WINDOW_TO)));
        }
    }

    @Test
    @DisplayName("CEM-008 AC1 the four quarter-hour prices of an hour are averaged, keyed by the start of the hour")
    void meanPriceByHourAveragesTheQuarterHourPrices() {
        persist(NOON, 0.04);
        persist(NOON.plus(Duration.ofMinutes(15)), 0.06);
        persist(NOON.plus(Duration.ofMinutes(30)), 0.08);
        persist(NOON.plus(Duration.ofMinutes(45)), 0.10);
        persist(NOON.plus(Duration.ofHours(1)), 0.50);

        Map<Instant, Double> prices = getPriceRepository.meanPriceByHour(NOON, NOON.plus(Duration.ofHours(2)),
                MADRID);

        assertEquals(2, prices.size(), () -> "was " + prices);
        assertEquals(0.07, prices.get(NOON), 1e-12);
        assertEquals(0.50, prices.get(NOON.plus(Duration.ofHours(1))), 1e-12);
    }

    @Test
    @DisplayName("CEM-008 AC2 a single hourly price is the hour's price")
    void meanPriceByHourKeepsASingleHourlyPrice() {
        persist(NOON, 0.05);

        Map<Instant, Double> prices = getPriceRepository.meanPriceByHour(NOON, NOON.plus(Duration.ofHours(1)),
                MADRID);

        assertEquals(Map.of(NOON, 0.05), prices);
    }

    @Test
    @DisplayName("CEM-009 AC4 an hour without a price is absent")
    void meanPriceByHourLeavesOutAnHourWithoutAPrice() {
        persist(NOON, 0.05);
        persist(NOON.plus(Duration.ofHours(2)), 0.06);

        Map<Instant, Double> prices = getPriceRepository.meanPriceByHour(NOON, NOON.plus(Duration.ofHours(3)),
                MADRID);

        assertEquals(Map.of(NOON, 0.05, NOON.plus(Duration.ofHours(2)), 0.06), prices);
    }

    @Test
    @DisplayName("CEM-008 AC3 a negative and a zero price are kept as they are")
    void meanPriceByHourKeepsNegativeAndZeroPrices() {
        persist(NOON, -0.01);
        persist(NOON.plus(Duration.ofHours(1)), 0d);

        Map<Instant, Double> prices = getPriceRepository.meanPriceByHour(NOON, NOON.plus(Duration.ofHours(2)),
                MADRID);

        assertEquals(Map.of(NOON, -0.01, NOON.plus(Duration.ofHours(1)), 0d), prices);
    }

    @Test
    @DisplayName("EPR-001 CEM-008 the prices are read over a half-open interval")
    void meanPriceByHourIsHalfOpen() {
        persist(NOON.minus(Duration.ofMinutes(15)), 9.99);
        persist(NOON, 0.05);
        persist(NOON.plus(Duration.ofHours(1)), 9.99);

        Map<Instant, Double> prices = getPriceRepository.meanPriceByHour(NOON, NOON.plus(Duration.ofHours(1)),
                MADRID);

        assertEquals(Map.of(NOON, 0.05), prices);
    }

    private void persist(Instant time, double priceKWh) {
        persistOmiePricesRepository.persistPrices(List.of(new PriceByHour(priceKWh, time.atOffset(ZoneOffset.UTC))));
    }
}
