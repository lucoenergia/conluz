package org.lucoenergia.conluz.price;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
public class GetPriceRepositoryInfluxTest {

    @Autowired
    private GetPriceRepositoryInflux repository;

    @Test
    void testGetPriceByRangeOfDates() {

        OffsetDateTime startDate = OffsetDateTime.parse("2023-10-25T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-10-25T23:00:00Z");

        List<PriceByHour> result = repository.getPricesByRangeOfDates(startDate, endDate);

        Assertions.assertEquals(24, result.size());
    }
}
