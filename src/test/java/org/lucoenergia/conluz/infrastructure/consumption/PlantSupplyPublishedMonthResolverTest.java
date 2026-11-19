package org.lucoenergia.conluz.infrastructure.consumption;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.PlantSupplyPublishedMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The resolver against a real InfluxDB, on records in the shape the Datadis sync gives a plant's own supply: its
 * surplus in the consumption measurement without self-consumption, then its production, with that surplus stored
 * as 0 from the first production record on.
 */
class PlantSupplyPublishedMonthResolverTest extends BaseIntegrationTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired
    private GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    @Autowired
    private GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture consumptionFixture;
    @Autowired
    private DatadisProductionInfluxFixture productionFixture;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-19T10:00:00Z"));
    private final List<String> codes = new ArrayList<>();

    @AfterEach
    void tearDown() {
        codes.forEach(code -> {
            consumptionFixture.clear(code);
            productionFixture.clear(code);
        });
    }

    @Test
    @DisplayName("ENM-009 a plant's own supply with production and no consumption record has its latest production month")
    void aSupplyWithProductionOnlyHasItsLatestProductionMonth() {
        Supply supply = supply();
        productionFixture.writeAt(supply.getCode(), hours("2026-09-01", "2026-10-31"), 5f);

        assertEquals(Optional.of(YearMonth.of(2026, 10)), resolver().resolveLatestPublishedMonth(supply, MADRID));
    }

    /**
     * December 2024 holds the zeroed surplus of every hour, but only ten days of production: counted from both
     * sources it would be published, counted from production alone it is not.
     */
    @Test
    @DisplayName("ENM-009 the legacy surplus counts before the first production record, never on or after it")
    void theLegacySurplusCountsOnlyBeforeTheFirstProductionRecord() {
        clock.setInstant(Instant.parse("2025-01-15T10:00:00Z"));
        Supply supply = supply();
        consumptionFixture.writeAt(supply.getCode(), hours("2024-10-01", "2024-11-30"), 0.1f, null, 4f);
        consumptionFixture.writeAt(supply.getCode(), hours("2024-12-01", "2024-12-31"), 0.1f, null, 0f);
        productionFixture.writeAt(supply.getCode(), hours("2024-12-01", "2024-12-10"), 4f);

        assertEquals(Optional.of(YearMonth.of(2024, 11)), resolver().resolveLatestPublishedMonth(supply, MADRID));
    }

    @Test
    @DisplayName("ENM-009 a plant's own supply with legacy surplus only has its latest legacy month")
    void aSupplyWithLegacySurplusOnlyHasItsLatestLegacyMonth() {
        Supply supply = supply();
        consumptionFixture.writeAt(supply.getCode(), hours("2026-08-01", "2026-09-30"), 0.1f, null, 4f);

        assertEquals(Optional.of(YearMonth.of(2026, 9)), resolver().resolveLatestPublishedMonth(supply, MADRID));
    }

    @Test
    @DisplayName("ENM-009 a plant's own supply without any record in either source has no published month")
    void aSupplyWithoutAnyRecordHasNoPublishedMonth() {
        assertEquals(Optional.empty(), resolver().resolveLatestPublishedMonth(supply(), MADRID));
    }

    @Test
    @DisplayName("ENM-009 a plant's own supply whose records are all in the current month has no published month")
    void aSupplyWhoseRecordsAreAllInTheCurrentMonthHasNoPublishedMonth() {
        Supply supply = supply();
        productionFixture.writeAt(supply.getCode(), hours("2026-11-01", "2026-11-18"), 5f);

        assertEquals(Optional.empty(), resolver().resolveLatestPublishedMonth(supply, MADRID));
    }

    private PlantSupplyPublishedMonthResolver resolver() {
        return new PlantSupplyPublishedMonthResolverImpl(getDatadisConsumptionAggregateRepository,
                getDatadisProductionAggregateRepository, clock);
    }

    private Supply supply() {
        String code = "ES" + RandomStringUtils.random(20, false, true);
        codes.add(code);
        return SupplyMother.random().withCode(code).build();
    }

    /**
     * Every hour of the local days {@code [first, last]}.
     */
    private static List<Instant> hours(String first, String last) {
        Instant end = LocalDate.parse(last).plusDays(1).atStartOfDay(MADRID).toInstant();
        return Stream.iterate(LocalDate.parse(first).atStartOfDay(MADRID).toInstant(), time -> time.isBefore(end),
                time -> time.plus(Duration.ofHours(1))).toList();
    }
}
