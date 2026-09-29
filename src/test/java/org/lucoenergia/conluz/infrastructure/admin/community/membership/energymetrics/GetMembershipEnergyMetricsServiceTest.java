package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.GetMembershipEnergyMetricsService;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.ArgumentMatcher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers what the service itself decides: which supplies are aggregated, which period they are
 * computed over, and that every supply is computed over the same one. The per-supply figures are
 * stubbed; how they add up is pinned by {@code MembershipEnergyMetricsTest}.
 */
class GetMembershipEnergyMetricsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-05-01T00:00:00+02:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-05-31T23:00:00+02:00");

    private final GetMembershipsRepository getMembershipsRepository = mock(GetMembershipsRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final GetSupplyEnergyMetricsService supplyMetricsService = mock(GetSupplyEnergyMetricsService.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final ReferenceMonthResolver referenceMonthResolver = mock(ReferenceMonthResolver.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);

    private final GetMembershipEnergyMetricsService service = new GetMembershipEnergyMetricsServiceImpl(
            getMembershipsRepository, getSupplyRepository, supplyMetricsService, aggregateRepository,
            referenceMonthResolver, zoneResolver, new DateConverter(timeConfiguration));

    private final Supply first = SupplyMother.random().build();
    private final Supply second = SupplyMother.random().build();

    @BeforeEach
    void givenTheConfiguredZone() {
        when(zoneResolver.resolveZoneIdForCommunity(COMMUNITY_ID)).thenReturn(ZONE);
        when(timeConfiguration.getZoneId()).thenReturn(ZONE);
    }

    @Test
    void aMembershipThatDoesNotExistIsReportedAsNotFoundWithoutComputingAnything() {
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.empty());

        assertThrows(MembershipNotFoundException.class, () -> service.getEnergyMetrics(
                COMMUNITY_ID, USER_ID, START, END, null));

        verifyNoInteractions(supplyMetricsService);
    }

    @Test
    void anInvalidPeriodIsRejectedBeforeAnythingIsRead() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> service.getEnergyMetrics(COMMUNITY_ID, USER_ID, START, END,
                        EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.CONFLICTING_PERIOD, exception.getReason());
        verifyNoInteractions(getMembershipsRepository, getSupplyRepository, supplyMetricsService);
    }

    @Test
    void everySupplyOfTheMemberInTheCommunityIsComputedOverTheExplicitPeriod() {
        givenMembership();
        givenSupplies(first, second);
        givenSupplyMetrics(first, START, END, 24L, 60d, 40d, 10d);
        givenSupplyMetrics(second, START, END, 10L, 1d, 1d, 3d);

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, USER_ID, START, END, null);

        assertEquals(START, metrics.getStartDate());
        assertEquals(END, metrics.getEndDate());
        assertEquals(61d, metrics.getEnergyBalance().getGridImportKWh(), 1e-9);
        assertEquals(41d, metrics.getEnergyBalance().getSelfConsumptionKWh(), 1e-9);
        assertEquals(13d, metrics.getEnergyBalance().getSurplusKWh(), 1e-9);
        assertEquals(34L, metrics.getHoursWithData());
        assertEquals(2, metrics.getSupplyCount());
        verifyNoInteractions(referenceMonthResolver);
    }

    /**
     * July 2026 is entirely in summer time, so both bounds carry +02:00, and the period ends on
     * the last hour of the month rather than on the following midnight.
     */
    @Test
    void theReferencePeriodIsTheResolvedMonthFromItsFirstToItsLastHour() {
        givenMembership();
        givenSupplies(first, second);
        when(referenceMonthResolver.resolveLatestPublishedMonth(List.of(first, second), ZONE))
                .thenReturn(Optional.of(YearMonth.of(2026, 7)));
        OffsetDateTime julyStart = OffsetDateTime.parse("2026-07-01T00:00:00+02:00");
        OffsetDateTime julyEnd = OffsetDateTime.parse("2026-07-31T23:00:00+02:00");
        givenSupplyMetrics(first, julyStart, julyEnd, 744L, 1d, 1d, 1d);
        givenSupplyMetrics(second, julyStart, julyEnd, 744L, 1d, 1d, 1d);

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, USER_ID, null, null,
                EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH);

        assertEquals(julyStart, metrics.getStartDate());
        assertEquals(julyEnd, metrics.getEndDate());
        verify(supplyMetricsService).getEnergyMetrics(SupplyId.of(first.getId()), julyStart, julyEnd);
        verify(supplyMetricsService).getEnergyMetrics(SupplyId.of(second.getId()), julyStart, julyEnd);
    }

    /**
     * October 2026 leaves summer time on its last Sunday, so each bound carries the offset in
     * force at its own instant.
     */
    @Test
    void eachBoundOfTheReferenceMonthCarriesTheOffsetInForceAtIt() {
        givenMembership();
        givenSupplies(first);
        when(referenceMonthResolver.resolveLatestPublishedMonth(List.of(first), ZONE))
                .thenReturn(Optional.of(YearMonth.of(2026, 10)));
        OffsetDateTime octoberStart = OffsetDateTime.parse("2026-10-01T00:00:00+02:00");
        OffsetDateTime octoberEnd = OffsetDateTime.parse("2026-10-31T23:00:00+01:00");
        givenSupplyMetrics(first, octoberStart, octoberEnd, 745L, 1d, 1d, 1d);

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, USER_ID, null, null,
                EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH);

        assertEquals(octoberStart, metrics.getStartDate());
        assertEquals(octoberEnd, metrics.getEndDate());
    }

    @Test
    void noPublishedMonthInTheWindowResolvesNoPeriodAndComputesNothing() {
        givenMembership();
        givenSupplies(first, second);
        when(referenceMonthResolver.resolveLatestPublishedMonth(List.of(first, second), ZONE))
                .thenReturn(Optional.empty());

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, USER_ID, null, null,
                EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH);

        assertNull(metrics.getStartDate());
        assertNull(metrics.getEndDate());
        assertNull(metrics.getSavings().getAmountEur());
        assertEquals(2, metrics.getSupplyCount());
        verifyNoInteractions(supplyMetricsService);
    }

    /**
     * Without dates or a reference period the period spans the membership's stored records: from
     * the first supply's earliest record to the second supply's latest one. Both supplies are
     * computed over that same span, so the one with the shorter history still counts against
     * coverage.
     */
    @Test
    void withoutAnyPeriodEverySupplyIsComputedOverTheWholeRecordedRangeOfTheMembership() {
        givenMembership();
        givenSupplies(first, second);
        when(aggregateRepository.findRecordedPeriod(first)).thenReturn(Optional.of(new RecordedConsumptionPeriod(
                Instant.parse("2025-01-10T09:00:00Z"), Instant.parse("2025-03-01T09:00:00Z"))));
        when(aggregateRepository.findRecordedPeriod(second)).thenReturn(Optional.of(new RecordedConsumptionPeriod(
                Instant.parse("2025-02-01T09:00:00Z"), Instant.parse("2025-04-20T09:00:00Z"))));
        OffsetDateTime rangeStart = OffsetDateTime.parse("2025-01-10T10:00:00+01:00");
        OffsetDateTime rangeEnd = OffsetDateTime.parse("2025-04-20T11:00:00+02:00");
        givenSupplyMetrics(first, rangeStart, rangeEnd, 10L, 1d, 1d, 1d);
        givenSupplyMetrics(second, rangeStart, rangeEnd, 10L, 1d, 1d, 1d);

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, USER_ID, null, null, null);

        assertEquals(rangeStart.toInstant(), metrics.getStartDate().toInstant());
        assertEquals(rangeEnd.toInstant(), metrics.getEndDate().toInstant());
        verify(supplyMetricsService).getEnergyMetrics(eq(SupplyId.of(first.getId())), eq(rangeStart), eq(rangeEnd));
        verify(supplyMetricsService).getEnergyMetrics(eq(SupplyId.of(second.getId())), eq(rangeStart), eq(rangeEnd));
    }

    @Test
    void withoutAnyPeriodAndWithoutAnyRecordNoPeriodIsResolved() {
        givenMembership();
        givenSupplies(first, second);
        when(aggregateRepository.findRecordedPeriod(any(Supply.class))).thenReturn(Optional.empty());

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, USER_ID, null, null, null);

        assertNull(metrics.getStartDate());
        assertNull(metrics.getSavings().getAmountEur());
        verifyNoInteractions(supplyMetricsService);
    }

    @Test
    void anExplicitPeriodResolvesEvenForAMembershipWithoutSupplies() {
        givenMembership();
        givenSupplies();

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, USER_ID, START, END, null);

        assertEquals(START, metrics.getStartDate());
        assertEquals(END, metrics.getEndDate());
        assertEquals(0, BigDecimal.ZERO.compareTo(metrics.getSavings().getAmountEur()));
        assertEquals(0, metrics.getSupplyCount());
    }

    @Test
    void aMembershipWithoutSuppliesResolvesNoReferencePeriod() {
        givenMembership();
        givenSupplies();
        when(referenceMonthResolver.resolveLatestPublishedMonth(List.of(), ZONE)).thenReturn(Optional.empty());

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, USER_ID, null, null,
                EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH);

        assertNull(metrics.getStartDate());
        assertNull(metrics.getSavings().getAmountEur());
        verify(supplyMetricsService, never()).getEnergyMetrics(any(), any(), any());
    }

    // --- helpers ---

    private void givenMembership() {
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(UserMother.randomUser())
                .withCommunity(CommunityMother.random().build())
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.of(membership));
    }

    /**
     * Matched on the wrapped id: {@link UserId} has no value equality.
     */
    private void givenSupplies(Supply... supplies) {
        when(getSupplyRepository.findAllByOwnerAndCommunityId(argThat(ownerId(USER_ID)), eq(COMMUNITY_ID)))
                .thenReturn(List.of(supplies));
    }

    private static ArgumentMatcher<UserId> ownerId(UUID expected) {
        return actual -> actual != null && expected.equals(actual.getId());
    }

    private void givenSupplyMetrics(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                                    long hoursWithData, double gridImportKWh, double selfConsumptionKWh,
                                    double surplusKWh) {
        when(supplyMetricsService.getEnergyMetrics(SupplyId.of(supply.getId()), startDate, endDate))
                .thenReturn(new SupplyEnergyMetrics(supply, startDate, endDate, hoursWithData, 24L,
                        gridImportKWh, selfConsumptionKWh, surplusKWh,
                        SupplySavings.of(BigDecimal.ONE, TariffSource.ESTIMATE, null)));
    }
}
