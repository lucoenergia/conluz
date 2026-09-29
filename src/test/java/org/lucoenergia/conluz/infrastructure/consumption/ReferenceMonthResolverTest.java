package org.lucoenergia.conluz.infrastructure.consumption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The store is stubbed with the latest record carrying assigned production per supply, which is
 * the contract the resolver consumes; which records qualify is pinned against a real InfluxDB in
 * {@code GetDatadisConsumptionAggregateRepositoryInfluxIntegrationTest}.
 *
 * <p>The clock is fixed at 2026-09-29, so the current month is September 2026 and the previous
 * calendar month is August 2026.
 */
class ReferenceMonthResolverTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

    /** 2024-09-01 00:00 in Madrid (+02:00): the first instant of the 24-month window. */
    private static final Instant WINDOW_START = Instant.parse("2024-08-31T22:00:00Z");
    /** 2026-09-01 00:00 in Madrid (+02:00): the first instant of the current month, excluded. */
    private static final Instant WINDOW_END = Instant.parse("2026-08-31T22:00:00Z");

    private final GetDatadisConsumptionAggregateRepository repository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final ClockProvider clockProvider = mock(ClockProvider.class);

    private final ReferenceMonthResolver resolver = new ReferenceMonthResolverImpl(repository, clockProvider);

    @BeforeEach
    void givenAFixedClock() {
        when(clockProvider.now()).thenReturn(NOW);
    }

    /**
     * August has not been published yet, so the latest record with assigned production is in July:
     * July is the reference month, not an empty August.
     */
    @Test
    void anUnpublishedPreviousMonthResolvesToTheLatestMonthWithAssignedProduction() {
        Supply supply = SupplyMother.random().build();
        givenLatestRecord(supply, "2026-07-31T21:00:00Z");

        assertEquals(Optional.of(YearMonth.of(2026, 7)),
                resolver.resolveLatestPublishedMonth(List.of(supply), ZONE));
    }

    /**
     * The month of the latest supply wins, whatever the order the supplies come in: the first and
     * the last supply are both behind the one in the middle.
     */
    @Test
    void theLatestMonthAcrossEverySupplyIsResolved() {
        Supply mayOnly = SupplyMother.random().build();
        Supply upToAugust = SupplyMother.random().build();
        Supply upToJune = SupplyMother.random().build();
        givenLatestRecord(mayOnly, "2026-05-15T10:00:00Z");
        givenLatestRecord(upToAugust, "2026-08-10T10:00:00Z");
        givenLatestRecord(upToJune, "2026-06-20T10:00:00Z");

        assertEquals(Optional.of(YearMonth.of(2026, 8)),
                resolver.resolveLatestPublishedMonth(List.of(mayOnly, upToAugust, upToJune), ZONE));
    }

    /**
     * 2026-06-30T22:30Z is already 1 July 00:30 in Madrid, so the month is read in the zone, not in
     * UTC.
     */
    @Test
    void theMonthIsReadInTheGivenZone() {
        Supply supply = SupplyMother.random().build();
        givenLatestRecord(supply, "2026-06-30T22:30:00Z");

        assertEquals(Optional.of(YearMonth.of(2026, 7)),
                resolver.resolveLatestPublishedMonth(List.of(supply), ZONE));
    }

    /**
     * The store is asked for the 24 complete months before the current one, with the current
     * month excluded, both bounds on month starts in the zone.
     */
    @Test
    void theSearchCoversTheTwentyFourCompleteMonthsBeforeTheCurrentOne() {
        Supply supply = SupplyMother.random().build();
        givenLatestRecord(supply, "2026-07-31T21:00:00Z");

        resolver.resolveLatestPublishedMonth(List.of(supply), ZONE);

        verify(repository).findLatestAssignedProductionRecord(supply, WINDOW_START, WINDOW_END);
    }

    @Test
    void noAssignedProductionInTheWindowResolvesNoMonth() {
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        when(repository.findLatestAssignedProductionRecord(any(Supply.class), any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertTrue(resolver.resolveLatestPublishedMonth(List.of(first, second), ZONE).isEmpty());
    }

    @Test
    void noSuppliesResolveNoMonthWithoutQueryingTheStore() {
        assertTrue(resolver.resolveLatestPublishedMonth(List.of(), ZONE).isEmpty());

        verifyNoInteractions(repository);
    }

    private void givenLatestRecord(Supply supply, String instant) {
        when(repository.findLatestAssignedProductionRecord(eq(supply), any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.of(Instant.parse(instant)));
    }
}
