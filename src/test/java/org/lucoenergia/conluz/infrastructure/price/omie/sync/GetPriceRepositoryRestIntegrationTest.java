package org.lucoenergia.conluz.infrastructure.price.omie.sync;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("These tests should not be included in a CI pipeline because connects with www.omie.es.")
class GetPriceRepositoryRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetPriceRepositoryRest repository;

    @Test
    void syncDailyPrices_successfulResponseWithNumberOne() {

        // Assemble
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        OffsetDateTime startDate = LocalDate.parse("20230528", formatter).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDate = LocalDate.parse("20230602", formatter).atStartOfDay().atOffset(ZoneOffset.UTC);

        // Act
        List<PriceByHour> prices = repository.getPricesByRangeOfDates(startDate, endDate);

        // Assert
        assertEquals(24, prices.size());
    }
}
