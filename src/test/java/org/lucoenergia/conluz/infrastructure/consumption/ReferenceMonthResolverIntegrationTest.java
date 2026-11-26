package org.lucoenergia.conluz.infrastructure.consumption;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
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
 * The resolver against a real InfluxDB, so its result does not depend on how it reads the store: the stubbed
 * {@link ReferenceMonthResolverTest} pins the walk, this one pins the answer for records as the Datadis sync stores
 * them.
 *
 * <p>The clock is fixed at 2026-10-08, so the current month is October 2026 and the latest candidate is September
 * 2026. Months are read in Europe/Madrid.
 */
class ReferenceMonthResolverIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired
    private GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture consumptionFixture;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T10:00:00Z"));
    private final List<String> codes = new ArrayList<>();

    @AfterEach
    void tearDown() {
        codes.forEach(consumptionFixture::clear);
    }

    @Test
    @DisplayName("ENM-001 a supply whose assigned production started mid-month is judged from its first published record")
    void aSupplyWhoseAssignedProductionStartedMidMonthIsJudgedFromItsFirstPublishedRecord() {
        Supply supply = supply();
        consumptionFixture.writeAt(supply.getCode(), hours("2026-09-16", "2026-09-30"), 0.4f, 0f, 0f);

        assertEquals(Optional.of(YearMonth.of(2026, 9)), resolve(supply));
    }

    @Test
    @DisplayName("ENM-002 a complete month is resolved rather than the following one holding only its first days")
    void aCompleteMonthIsResolvedRatherThanTheFollowingOneHoldingOnlyItsFirstDays() {
        Supply supply = supply();
        consumptionFixture.writeAt(supply.getCode(), hours("2026-08-01", "2026-09-05"), 0.4f, 0.1f, 0f);

        assertEquals(Optional.of(YearMonth.of(2026, 8)), resolve(supply));
    }

    /**
     * The first supply never publishes; the third holds only the first days of September, which walks the search
     * back past September and August to the second supply's July.
     */
    @Test
    @DisplayName("ENM-002 the latest month published for any of several supplies is resolved, walking back past unpublished ones")
    void theLatestMonthPublishedForAnyOfSeveralSuppliesIsResolved() {
        Supply neverPublishes = supply();
        Supply publishesJuly = supply();
        Supply startsInSeptember = supply();
        consumptionFixture.writeAt(neverPublishes.getCode(), hours("2026-06-01", "2026-09-30"), 0.4f, null, 0.2f);
        consumptionFixture.writeAt(publishesJuly.getCode(), hours("2026-06-01", "2026-07-31"), 0.4f, 0f, 0f);
        consumptionFixture.writeAt(startsInSeptember.getCode(), hours("2026-09-01", "2026-09-03"), 0.4f, 0.1f, 0f);

        assertEquals(Optional.of(YearMonth.of(2026, 7)),
                resolver().resolveLatestPublishedMonth(List.of(neverPublishes, publishesJuly, startsInSeptember),
                        MADRID));
    }

    @Test
    @DisplayName("ENM-001 the hours of a month are counted in the zone across a daylight saving change")
    void theHoursOfAMonthAreCountedInTheZoneAcrossADaylightSavingChange() {
        Supply supply = supply();
        consumptionFixture.writePublishedZeros(supply.getCode(), YearMonth.of(2026, 3), MADRID);

        assertEquals(Optional.of(YearMonth.of(2026, 3)), resolve(supply));
    }

    @Test
    @DisplayName("ENM-003 supplies with records but none published resolve no month")
    void suppliesWithRecordsButNonePublishedResolveNoMonth() {
        Supply first = supply();
        Supply second = supply();
        consumptionFixture.writeAt(first.getCode(), hours("2026-08-01", "2026-09-30"), 0.4f, null, 0.2f);
        consumptionFixture.writeAt(second.getCode(), hours("2026-10-01", "2026-10-07"), 0.4f, 0.1f, 0f);

        assertEquals(Optional.empty(),
                resolver().resolveLatestPublishedMonth(List.of(first, second, supply()), MADRID));
    }

    @Test
    @DisplayName("ENM-003 no supplies resolve no month")
    void noSuppliesResolveNoMonth() {
        assertEquals(Optional.empty(), resolver().resolveLatestPublishedMonth(List.of(), MADRID));
    }

    private Optional<YearMonth> resolve(Supply supply) {
        return resolver().resolveLatestPublishedMonth(List.of(supply), MADRID);
    }

    private ReferenceMonthResolver resolver() {
        return new ReferenceMonthResolverImpl(getDatadisConsumptionAggregateRepository, clock);
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
