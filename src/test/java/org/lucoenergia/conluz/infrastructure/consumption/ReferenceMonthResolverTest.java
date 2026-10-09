package org.lucoenergia.conluz.infrastructure.consumption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.time.ClockProvider;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The store is stubbed with what the resolver consumes: each supply's first and last published
 * record in the window, and its published hours per month. Which records count as published is
 * pinned against a real InfluxDB in {@code GetDatadisConsumptionAggregateRepositoryInfluxIntegrationTest}.
 *
 * <p>Unless a test moves it, the clock is fixed at 2026-10-08, so the current month is October 2026
 * and the latest candidate is September 2026. Months are read in Europe/Madrid: a month in summer
 * time starts at 22:00Z of the day before, one in winter time at 23:00Z.
 */
class ReferenceMonthResolverTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    /** 2024-10-01 00:00 in Madrid (+02:00): the first instant of the 24-month window. */
    private static final Instant WINDOW_START = Instant.parse("2024-09-30T22:00:00Z");
    /** 2026-10-01 00:00 in Madrid (+02:00): the first instant of the current month, excluded. */
    private static final Instant WINDOW_END = Instant.parse("2026-09-30T22:00:00Z");

    private final GetDatadisConsumptionAggregateRepository repository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final ClockProvider clockProvider = mock(ClockProvider.class);

    private final ReferenceMonthResolver resolver = new ReferenceMonthResolverImpl(repository, clockProvider);

    @BeforeEach
    void givenAFixedClock() {
        when(clockProvider.now()).thenReturn(NOW);
    }

    /**
     * August is published in full; September holds only its first five days, 120 of its 720 hours.
     * The complete August is resolved, not the partial September.
     */
    @Test
    @DisplayName("ENM-002 AC1 a complete month is resolved rather than the following one holding only its first days")
    void aCompleteMonthIsResolvedRatherThanTheFollowingOneHoldingOnlyItsFirstDays() {
        Supply supply = SupplyMother.random().build();
        givenPublishedPeriod(supply, "2025-01-01T00:00:00Z", "2026-09-05T21:00:00Z");
        givenPublishedHours(supply, YearMonth.of(2026, 9), 120);
        givenPublishedHours(supply, YearMonth.of(2026, 8), 744);

        assertEquals(Optional.of(YearMonth.of(2026, 8)), resolve(supply));
    }

    /**
     * On 2 October September is already published in full: it is resolved at once, not a month
     * later.
     */
    @Test
    @DisplayName("ENM-002 AC2 a month published in full is resolved as soon as it qualifies")
    void aMonthPublishedInFullIsResolvedAsSoonAsItQualifies() {
        when(clockProvider.now()).thenReturn(Instant.parse("2026-10-02T06:00:00Z"));
        Supply supply = SupplyMother.random().build();
        givenPublishedPeriod(supply, "2025-01-01T00:00:00Z", "2026-09-30T21:00:00Z");
        givenPublishedHours(supply, YearMonth.of(2026, 9), 720);
        givenPublishedHours(supply, YearMonth.of(2026, 8), 744);

        assertEquals(Optional.of(YearMonth.of(2026, 9)), resolve(supply));
    }

    /**
     * The supply's assigned production starts on 15 August at 00:00 in Madrid and every hour after
     * it is published: 408 hours. Against the whole month that is 408 / 744 = 55 %, so a rule
     * judging the whole month would skip August for good; judged from the supply's first published
     * record it is complete.
     */
    @Test
    @DisplayName("ENM-001 AC3 a supply whose assigned production started mid-month is judged from its first published record")
    void aSupplyWhoseAssignedProductionStartedMidMonthIsJudgedFromItsFirstPublishedRecord() {
        Supply supply = SupplyMother.random().build();
        givenPublishedPeriod(supply, "2026-08-14T22:00:00Z", "2026-08-31T21:00:00Z");
        givenPublishedHours(supply, YearMonth.of(2026, 8), 408);

        assertEquals(Optional.of(YearMonth.of(2026, 8)), resolve(supply));
    }

    /**
     * A supply whose every month in the window holds half of its hours published never reaches the
     * threshold: nothing is resolved, rather than the best of those months, and the walk stops at
     * the start of the window.
     */
    @Test
    @DisplayName("ENM-003 AC5 no month in the window published for any supply resolves no month")
    void noMonthInTheWindowPublishedForAnySupplyResolvesNoMonth() {
        Supply supply = SupplyMother.random().build();
        givenPublishedPeriod(supply, "2024-09-30T22:00:00Z", "2026-09-30T21:00:00Z");
        when(repository.countPublishedHours(eq(supply), any(Instant.class), any(Instant.class))).thenReturn(360L);

        assertTrue(resolve(supply).isEmpty());
        verify(repository, never()).countPublishedHours(supply, startOf(YearMonth.of(2024, 9)),
                startOf(YearMonth.of(2024, 10)));
    }

    /**
     * June has 720 hours, so 90 % is exactly 648: 648 published hours qualify.
     */
    @Test
    @DisplayName("ENM-001 a month with exactly the threshold share of its hours published is published")
    void aMonthWithExactlyTheThresholdShareOfItsHoursPublishedIsPublished() {
        when(clockProvider.now()).thenReturn(Instant.parse("2026-07-10T10:00:00Z"));
        Supply supply = SupplyMother.random().build();
        givenPublishedPeriod(supply, "2025-01-01T00:00:00Z", "2026-06-30T21:00:00Z");
        givenPublishedHours(supply, YearMonth.of(2026, 6), 648);
        givenPublishedHours(supply, YearMonth.of(2026, 5), 744);

        assertEquals(Optional.of(YearMonth.of(2026, 6)), resolve(supply));
    }

    /**
     * One hour short of the threshold, June is not published and May is resolved.
     */
    @Test
    @DisplayName("ENM-001 a month one hour short of the threshold share is not published")
    void aMonthOneHourShortOfTheThresholdShareIsNotPublished() {
        when(clockProvider.now()).thenReturn(Instant.parse("2026-07-10T10:00:00Z"));
        Supply supply = SupplyMother.random().build();
        givenPublishedPeriod(supply, "2025-01-01T00:00:00Z", "2026-06-30T21:00:00Z");
        givenPublishedHours(supply, YearMonth.of(2026, 6), 647);
        givenPublishedHours(supply, YearMonth.of(2026, 5), 744);

        assertEquals(Optional.of(YearMonth.of(2026, 5)), resolve(supply));
    }

    /**
     * October 2026 has 745 hours in Madrid, the clocks going back on the 25th, so 90 % is 670.5 and
     * 670 published hours fall short. Counted as 744 UTC hours, 670 would have been enough.
     */
    @Test
    @DisplayName("ENM-001 the hours of a month are counted in the zone across a daylight saving change")
    void theHoursOfAMonthAreCountedInTheZoneAcrossADaylightSavingChange() {
        when(clockProvider.now()).thenReturn(Instant.parse("2026-11-10T10:00:00Z"));
        Supply supply = SupplyMother.random().build();
        givenPublishedPeriod(supply, "2025-01-01T00:00:00Z", "2026-10-31T22:00:00Z");
        givenPublishedHours(supply, YearMonth.of(2026, 10), 670);
        givenPublishedHours(supply, YearMonth.of(2026, 9), 720);

        assertEquals(Optional.of(YearMonth.of(2026, 9)), resolve(supply));

        givenPublishedHours(supply, YearMonth.of(2026, 10), 671);

        assertEquals(Optional.of(YearMonth.of(2026, 10)), resolve(supply));
    }

    /**
     * The month of the latest published supply wins, whatever the order the supplies come in: the
     * first and the last supply are both behind the one in the middle.
     */
    @Test
    @DisplayName("ENM-002 the latest month published for any supply is resolved")
    void theLatestMonthPublishedForAnySupplyIsResolved() {
        Supply upToMay = SupplyMother.random().build();
        Supply upToAugust = SupplyMother.random().build();
        Supply upToJune = SupplyMother.random().build();
        givenPublishedPeriod(upToMay, "2025-01-01T00:00:00Z", "2026-05-31T21:00:00Z");
        givenPublishedPeriod(upToAugust, "2025-01-01T00:00:00Z", "2026-08-31T21:00:00Z");
        givenPublishedPeriod(upToJune, "2025-01-01T00:00:00Z", "2026-06-30T21:00:00Z");
        givenPublishedHours(upToMay, YearMonth.of(2026, 5), 744);
        givenPublishedHours(upToAugust, YearMonth.of(2026, 8), 744);
        givenPublishedHours(upToJune, YearMonth.of(2026, 6), 720);

        assertEquals(Optional.of(YearMonth.of(2026, 8)),
                resolver.resolveLatestPublishedMonth(List.of(upToMay, upToAugust, upToJune), ZONE));
    }

    /**
     * A supply that never publishes -- a plant's own supply, or one outside the sharing -- does not
     * keep its owner's other supply from resolving its published month.
     */
    @Test
    @DisplayName("ENM-002 a supply that never publishes does not keep another supply's month from resolving")
    void aSupplyThatNeverPublishesDoesNotKeepAnotherSupplysMonthFromResolving() {
        Supply neverPublishes = SupplyMother.random().build();
        Supply published = SupplyMother.random().build();
        givenPublishedPeriod(published, "2025-01-01T00:00:00Z", "2026-08-31T21:00:00Z");
        givenPublishedHours(published, YearMonth.of(2026, 8), 743);

        assertEquals(Optional.of(YearMonth.of(2026, 8)),
                resolver.resolveLatestPublishedMonth(List.of(neverPublishes, published), ZONE));
    }

    /**
     * The watched assumption: August is published for one supply and only begun for the other. The
     * month is resolved all the same, carrying the published supply's assigned production alone.
     */
    @Test
    @DisplayName("ENM-002 a month published for only one of the supplies is resolved")
    void aMonthPublishedForOnlyOneOfTheSuppliesIsResolved() {
        Supply published = SupplyMother.random().build();
        Supply lagging = SupplyMother.random().build();
        givenPublishedPeriod(published, "2025-01-01T00:00:00Z", "2026-08-31T21:00:00Z");
        givenPublishedPeriod(lagging, "2025-01-01T00:00:00Z", "2026-08-03T21:00:00Z");
        givenPublishedHours(published, YearMonth.of(2026, 8), 744);
        givenPublishedHours(lagging, YearMonth.of(2026, 8), 72);

        assertEquals(Optional.of(YearMonth.of(2026, 8)),
                resolver.resolveLatestPublishedMonth(List.of(lagging, published), ZONE));
    }

    /**
     * The last published record sits at 22:00Z on 31 August, already 1 September 00:00 in Madrid,
     * so the month counted and resolved is September, with its bounds in the zone.
     */
    @Test
    @DisplayName("ENM-002 the months are read in the given zone")
    void theMonthsAreReadInTheGivenZone() {
        Supply supply = SupplyMother.random().build();
        givenPublishedPeriod(supply, "2025-01-01T00:00:00Z", "2026-08-31T22:00:00Z");
        givenPublishedHours(supply, YearMonth.of(2026, 9), 720);

        assertEquals(Optional.of(YearMonth.of(2026, 9)), resolve(supply));
    }

    /**
     * The store is asked for the 24 complete months before the current one, with the current
     * month excluded, both bounds on month starts in the zone.
     */
    @Test
    @DisplayName("ENM-002 the search covers the 24 complete months before the current one")
    void theSearchCoversTheTwentyFourCompleteMonthsBeforeTheCurrentOne() {
        Supply supply = SupplyMother.random().build();

        resolve(supply);

        verify(repository).findPublishedPeriod(supply, WINDOW_START, WINDOW_END);
    }

    @Test
    @DisplayName("ENM-003 no published record in the window resolves no month without counting any hour")
    void noPublishedRecordInTheWindowResolvesNoMonthWithoutCountingAnyHour() {
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        when(repository.findPublishedPeriod(any(Supply.class), any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertTrue(resolver.resolveLatestPublishedMonth(List.of(first, second), ZONE).isEmpty());
        verify(repository, never()).countPublishedHours(any(Supply.class), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("ENM-003 no supplies resolve no month without querying the store")
    void noSuppliesResolveNoMonthWithoutQueryingTheStore() {
        assertTrue(resolver.resolveLatestPublishedMonth(List.of(), ZONE).isEmpty());

        verifyNoInteractions(repository);
    }

    private Optional<YearMonth> resolve(Supply supply) {
        return resolver.resolveLatestPublishedMonth(List.of(supply), ZONE);
    }

    private void givenPublishedPeriod(Supply supply, String firstRecord, String lastRecord) {
        when(repository.findPublishedPeriod(eq(supply), any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.of(new RecordedConsumptionPeriod(Instant.parse(firstRecord),
                        Instant.parse(lastRecord))));
    }

    private void givenPublishedHours(Supply supply, YearMonth month, long hours) {
        when(repository.countPublishedHours(supply, startOf(month), startOf(month.plusMonths(1))))
                .thenReturn(hours);
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZONE).toInstant();
    }
}
