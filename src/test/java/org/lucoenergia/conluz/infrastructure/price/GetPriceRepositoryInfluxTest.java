package org.lucoenergia.conluz.infrastructure.price;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryInflux;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
class GetPriceRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    private GetPriceRepositoryInflux repository;
    @Autowired
    private EnergyPricesInfluxLoader energyPricesInfluxLoader;

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
}
