package org.lucoenergia.conluz.infrastructure.price;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryInflux3;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
class GetPriceRepositoryInflux3Test extends BaseIntegrationTest {

    @Autowired
    @Qualifier("getPriceRepositoryInflux3")
    private GetPriceRepositoryInflux3 repository;

    @Autowired
    private EnergyPricesInflux3Loader energyPricesInflux3Loader;

    @BeforeEach
    void beforeEach() {
        energyPricesInflux3Loader.loadData();
    }

    @AfterEach
    void afterEach() {
        energyPricesInflux3Loader.clearData();
    }

    @Test
    void testGetPriceByRangeOfDates() {
        // Arrange
        OffsetDateTime startDate = OffsetDateTime.parse("2023-10-25T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-10-25T23:00:00.000+02:00");

        // Act
        List<PriceByHour> result = repository.getPricesByRangeOfDates(startDate, endDate);

        // Assert
        Assertions.assertEquals(24, result.size());
    }

    @Test
    void testGetPriceByRangeOfDatesVerifyFirstPrice() {
        // Arrange
        OffsetDateTime startDate = OffsetDateTime.parse("2023-10-25T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-10-25T23:00:00.000+02:00");

        // Act
        List<PriceByHour> result = repository.getPricesByRangeOfDates(startDate, endDate);

        // Assert
        Assertions.assertFalse(result.isEmpty());
        PriceByHour firstPrice = result.get(0);
        Assertions.assertNotNull(firstPrice.getHour());
        Assertions.assertNotNull(firstPrice.getPrice());
        Assertions.assertEquals(114.1d, firstPrice.getPrice(), 0.01d);
    }

    @Test
    void testGetPriceByRangeOfDatesVerifyLastPrice() {
        // Arrange
        OffsetDateTime startDate = OffsetDateTime.parse("2023-10-25T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-10-25T23:00:00.000+02:00");

        // Act
        List<PriceByHour> result = repository.getPricesByRangeOfDates(startDate, endDate);

        // Assert
        Assertions.assertEquals(24, result.size());
        PriceByHour lastPrice = result.get(23);
        Assertions.assertNotNull(lastPrice.getHour());
        Assertions.assertNotNull(lastPrice.getPrice());
        Assertions.assertEquals(105.97d, lastPrice.getPrice(), 0.01d);
    }

    @Test
    void testGetPriceByRangeOfDatesEmptyResult() {
        // Arrange - Query a date range with no data
        OffsetDateTime startDate = OffsetDateTime.parse("2024-01-01T00:00:00.000+02:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2024-01-01T23:00:00.000+02:00");

        // Act
        List<PriceByHour> result = repository.getPricesByRangeOfDates(startDate, endDate);

        // Assert
        Assertions.assertTrue(result.isEmpty());
    }
}
