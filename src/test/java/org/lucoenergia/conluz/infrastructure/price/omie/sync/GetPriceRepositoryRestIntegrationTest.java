package org.lucoenergia.conluz.infrastructure.price.omie.sync;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.price.omie.get.GetPriceRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

//@Disabled("These tests should not be included in a CI pipeline because connects with www.omie.es.")
class GetPriceRepositoryRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetPriceRepositoryRest repository;

    @Test
    void syncDailyPrices_successfulResponseWithNumberOne() {

        // Assemble
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate date = LocalDate.parse("20230528", formatter);
        OffsetDateTime dateTime = date.atStartOfDay().atOffset(ZoneOffset.UTC);

        // Act
        List<PriceByHour> prices = repository.getPricesByDay(dateTime);

        // Assert
        assertEquals(24, prices.size());
    }
}
