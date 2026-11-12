package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisDailyConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisHourlyConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The grouped per-supply reads count, per day and per hour, the records whose self-consumption has
 * been published, against a real InfluxDB: COUNT over the field skips a record where it is absent
 * and counts one where it is zero.
 */
class GetDatadisConsumptionRepositoryInfluxPublishedHoursIntegrationTest extends BaseIntegrationTest {

    // 10 May 2026, local midnight in Madrid.
    private static final OffsetDateTime DAY_START = OffsetDateTime.parse("2026-05-10T00:00:00+02:00");

    @Autowired
    @Qualifier("getDatadisConsumptionRepositoryInflux")
    private GetDatadisConsumptionRepository repository;
    @Autowired
    private DatadisConsumptionInfluxFixture fixture;

    private final List<String> createdCupsCodes = new ArrayList<>();

    @AfterEach
    void afterEach() {
        createdCupsCodes.forEach(fixture::clear);
    }

    @Test
    void aDayCountsItsRecordsCarryingSelfConsumptionZeroIncluded() {
        Supply supply = supply();
        Instant start = DAY_START.toInstant();
        fixture.writeAt(supply.getCode(), List.of(start, start.plusSeconds(3600)), 1f, 2f, 0f);
        fixture.writeAt(supply.getCode(), start.plusSeconds(2 * 3600), 1f, 0f, 0f);
        fixture.writeAt(supply.getCode(), List.of(start.plusSeconds(3 * 3600), start.plusSeconds(4 * 3600)),
                1f, null, 1f);
        // The next day: records without self-consumption only.
        fixture.writeAt(supply.getCode(), start.plusSeconds(25 * 3600), 1f, null, 1f);

        List<DatadisDailyConsumption> days = repository.getDailyConsumptionsByRangeOfDates(supply, DAY_START,
                DAY_START.plusDays(2));

        assertEquals(2, days.size());
        assertEquals(3L, days.get(0).getPublishedHours());
        assertEquals(5f, days.get(0).getConsumption().getConsumptionKWh(), 0f);
        assertEquals(0L, days.get(1).getPublishedHours());
        assertEquals(1f, days.get(1).getConsumption().getConsumptionKWh(), 0f);
    }

    @Test
    void anHourCountsItsRecordCarryingSelfConsumptionAndAnHourWithoutItCountsNone() {
        Supply supply = supply();
        Instant start = DAY_START.toInstant();
        fixture.writeAt(supply.getCode(), start, 1f, 0f, 0f);
        fixture.writeAt(supply.getCode(), start.plusSeconds(3600), 1f, null, 1f);
        fixture.writeAt(supply.getCode(), start.plusSeconds(2 * 3600), 1f, 3f, 0f);

        Map<Instant, Long> publishedHours = repository.getHourlyConsumptionsByPeriod(supply, DAY_START,
                        DAY_START.plusHours(3))
                .stream()
                .collect(Collectors.toMap(DatadisHourlyConsumption::getStartsAt,
                        DatadisHourlyConsumption::getPublishedHours));

        assertEquals(Map.of(start, 1L, start.plusSeconds(3600), 0L, start.plusSeconds(2 * 3600), 1L),
                publishedHours);
    }

    private Supply supply() {
        String code = "ES" + RandomStringUtils.random(20, false, true);
        createdCupsCodes.add(code);
        return SupplyMother.random().withCode(code).build();
    }
}
