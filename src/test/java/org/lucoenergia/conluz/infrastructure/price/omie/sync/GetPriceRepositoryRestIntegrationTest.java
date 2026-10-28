package org.lucoenergia.conluz.infrastructure.price.omie.sync;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.sync.OmieDayPrices;
import org.lucoenergia.conluz.domain.price.sync.GetOmiePricesRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("These tests should not be included in a CI pipeline because connects with www.omie.es.")
class GetPriceRepositoryRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetOmiePricesRepository repository;

    @Test
    void syncDailyPrices_successfulResponseWithNumberOne() {

        // Assemble
        LocalDate startDate = LocalDate.parse("2023-05-28");
        LocalDate endDate = LocalDate.parse("2023-06-03");

        // Act
        List<OmieDayPrices> days = repository.getPricesByDays(startDate, endDate);

        // Assert
        assertEquals(6, days.size());
        days.forEach(day -> assertEquals(OmieDayPrices.Status.SERVED, day.status()));
        assertEquals(24, days.get(0).prices().size());
    }
}
