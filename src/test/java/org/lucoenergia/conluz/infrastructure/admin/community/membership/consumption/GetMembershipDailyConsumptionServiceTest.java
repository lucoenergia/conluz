package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipDailyConsumptionService;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipConsumptionTotals;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipDailyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.consumption.EstimatedPrice;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolverImpl;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.ArgumentMatcher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers what the service itself decides: which supplies are read, over which bounds, which days
 * the series has, which supply's bucket lands in which day, and which buckets count as data. The
 * per-supply daily series is stubbed; how a day's buckets fold is pinned by
 * {@code MembershipConsumptionTotalsTest}.
 */
class GetMembershipDailyConsumptionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    /**
     * 11:00 on 27 October 2026 in Madrid, after every period the tests request but the last.
     */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final EstimatedPrice PRICE = EstimatedPrice.of(new BigDecimal("0.15"));

    /** Three local days, 10 to 12 January 2024. */
    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-10T00:00:00+01:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-13T00:00:00+01:00");

    private final GetMembershipsRepository getMembershipsRepository = mock(GetMembershipsRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final GetDatadisConsumptionService getDatadisConsumptionService = mock(GetDatadisConsumptionService.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final ReferenceMonthResolver referenceMonthResolver = mock(ReferenceMonthResolver.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);

    private final GetMembershipDailyConsumptionService service = new GetMembershipDailyConsumptionServiceImpl(
            new MembershipEnergyMetricsScopeResolverImpl(getMembershipsRepository, getSupplyRepository,
                    aggregateRepository, referenceMonthResolver, zoneResolver, new DateConverter(timeConfiguration),
                    CLOCK),
            getDatadisConsumptionService,
            zoneResolver);

    private final Supply first = SupplyMother.random().build();
    private final Supply second = SupplyMother.random().build();

    @BeforeEach
    void givenTheConfiguredZone() {
        when(zoneResolver.resolveZoneIdForCommunity(COMMUNITY_ID)).thenReturn(ZONE);
        when(timeConfiguration.getZoneId()).thenReturn(ZONE);
    }

    /**
     * AC1. On the 10th only the first supply stored records; on the 11th both did; the second
     * supply's 10th comes back from the per-supply series as a zero bucket without obtainMethod,
     * and is not counted.
     */
    @Test
    @DisplayName("MCS-002 AC1 a day counts and sums only the supplies that stored a record that day")
    void aDayCountsAndSumsOnlyTheSuppliesThatStoredARecord() {
        givenMembership();
        givenSupplies(first, second);
        givenDailySeries(first, START, END,
                day("2024/01/10", 10f, 2f, "0.30", "Real"),
                day("2024/01/11", 20f, 4f, "0.60", "Real"),
                day("2024/01/12", 0f, 0f, "0.00", null));
        givenDailySeries(second, START, END,
                day("2024/01/10", 0f, 0f, "0.00", null),
                day("2024/01/11", 5f, 1f, "0.15", "Estimada"),
                day("2024/01/12", 0f, 0f, "0.00", null));

        List<MembershipDailyConsumptionBucket> series = service.getDailySeries(COMMUNITY_ID, USER_ID, START, END);

        assertDays(series, "2024-01-10", "2024-01-11", "2024-01-12");
        assertDay(series.get(0), "10", "0.30", 2, 1);
        assertDay(series.get(1), "25", "0.75", 2, 2);
        MembershipConsumptionTotals empty = series.get(2).getTotals();
        assertDecimal("0", empty.getConsumptionKWh());
        assertTrue(empty.getSavings().isEmpty());
        assertEquals(2, empty.getSupplyCount());
        assertEquals(0, empty.getSuppliesWithData());
    }

    /**
     * Stored zeros are a measured zero: the supply has data and the day's savings are 0.00, not
     * empty.
     */
    @Test
    @DisplayName("MCS-001 AC2 a day of stored zeros has savings of zero and every supply with data")
    void aDayOfStoredZerosIsAMeasuredZero() {
        givenMembership();
        givenSupplies(first, second);
        givenDailySeries(first, START, END, day("2024/01/10", 0f, 0f, "0.00", "Real"));
        givenDailySeries(second, START, END, day("2024/01/10", 0f, 0f, "0.00", "Real"));

        MembershipConsumptionTotals totals =
                service.getDailySeries(COMMUNITY_ID, USER_ID, START, END).get(0).getTotals();

        assertEquals(2, totals.getSuppliesWithData());
        assertDecimal("0", totals.getSavings().orElseThrow().getAmountEur());
    }

    /**
     * One call to the per-supply series per supply, over the resolved period, whatever the number
     * of days.
     */
    @Test
    @DisplayName("MCS-002 every supply's daily series is read once, over the whole period")
    void everySupplysSeriesIsReadOnce() {
        givenMembership();
        givenSupplies(first, second);
        givenDailySeries(first, START, END);
        givenDailySeries(second, START, END);

        service.getDailySeries(COMMUNITY_ID, USER_ID, START, END);

        verify(getDatadisConsumptionService, times(1)).getDailySeriesBySupply(SupplyId.of(first.getId()), START, END);
        verify(getDatadisConsumptionService, times(1)).getDailySeriesBySupply(SupplyId.of(second.getId()), START, END);
        verifyNoMoreInteractions(getDatadisConsumptionService);
    }

    /**
     * The days are the community's local days. Bounds given in UTC, 23:00Z on the 9th to 23:00Z on
     * the 12th, are midnight to midnight in Madrid: the 10th to the 12th, not the 9th to the 12th
     * the UTC dates would give.
     */
    @Test
    @DisplayName("MCS-001 the days are local days of the community's time zone")
    void theDaysAreLocalDaysOfTheCommunityZone() {
        givenMembership();
        givenSupplies();

        assertDays(service.getDailySeries(COMMUNITY_ID, USER_ID,
                        OffsetDateTime.parse("2024-01-09T23:00:00Z"),
                        OffsetDateTime.parse("2024-01-12T23:00:00Z")),
                "2024-01-10", "2024-01-11", "2024-01-12");
    }

    /**
     * Every day overlapping the half-open bounds is emitted, the partial ones included: a start at
     * 12:00 on the 10th keeps the 10th, an end at 06:00 on the 12th keeps the 12th, and an end at
     * midnight of the 13th does not take the 13th in.
     */
    @Test
    @DisplayName("MCS-001 EPR-001 every day overlapping the half-open period is emitted, partial ones included")
    void everyDayOverlappingThePeriodIsEmitted() {
        givenMembership();
        givenSupplies();

        assertDays(service.getDailySeries(COMMUNITY_ID, USER_ID,
                        OffsetDateTime.parse("2024-01-10T12:00:00+01:00"),
                        OffsetDateTime.parse("2024-01-12T06:00:00+01:00")),
                "2024-01-10", "2024-01-11", "2024-01-12");
        assertDays(service.getDailySeries(COMMUNITY_ID, USER_ID, START, END),
                "2024-01-10", "2024-01-11", "2024-01-12");
    }

    /**
     * The clock stands at 11:00 on 27 October 2026 in Madrid. Every supply is read up to that hour,
     * and no day after it is emitted though the requested end reaches into November.
     */
    @Test
    @DisplayName("EPR-002 every supply and the days emitted follow the end clipped to the current hour")
    void everySupplyAndTheDaysEmittedFollowTheClippedEnd() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-26T00:00:00+01:00");
        OffsetDateTime clippedEnd = OffsetDateTime.parse("2026-10-27T11:00:00+01:00");
        givenMembership();
        givenSupplies(first);
        givenDailySeries(first, start, clippedEnd,
                day("2026/10/26", 24f, 0f, "0.00", "Real"),
                day("2026/10/27", 11f, 0f, "0.00", "Real"));

        List<MembershipDailyConsumptionBucket> series = service.getDailySeries(COMMUNITY_ID, USER_ID, start,
                OffsetDateTime.parse("2026-11-03T00:00:00+01:00"));

        assertDays(series, "2026-10-26", "2026-10-27");
        assertDecimal("11", series.get(1).getTotals().getConsumptionKWh());
    }

    /**
     * No supplies: every day, zero totals, no savings, a supply count of zero; the per-supply
     * series is never asked.
     */
    @Test
    @DisplayName("MCS-001 a membership without supplies gets every day empty")
    void aMembershipWithoutSuppliesGetsEveryDayEmpty() {
        givenMembership();
        givenSupplies();

        List<MembershipDailyConsumptionBucket> series = service.getDailySeries(COMMUNITY_ID, USER_ID, START, END);

        assertDays(series, "2024-01-10", "2024-01-11", "2024-01-12");
        for (MembershipDailyConsumptionBucket bucket : series) {
            assertTrue(bucket.getTotals().getSavings().isEmpty());
            assertEquals(0, bucket.getTotals().getSupplyCount());
            assertEquals(0, bucket.getTotals().getSuppliesWithData());
        }
        verifyNoInteractions(getDatadisConsumptionService);
    }

    @Test
    @DisplayName("EPR-001 a start after the end is rejected before anything is read")
    void aStartAfterTheEndIsRejectedBeforeAnythingIsRead() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> service.getDailySeries(COMMUNITY_ID, USER_ID, END, START));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END, exception.getReason());
        verifyNoInteractions(getMembershipsRepository, getSupplyRepository, getDatadisConsumptionService);
    }

    /**
     * Explicit dates always resolve a period, so this branch is not reachable through the
     * resolver; it mirrors the membership monthly series, which answers an empty series there.
     */
    @Test
    @DisplayName("MCS-001 a scope without a period gives an empty series, as the monthly series does")
    void aScopeWithoutAPeriodGivesAnEmptySeries() {
        MembershipEnergyMetricsScopeResolver resolver = mock(MembershipEnergyMetricsScopeResolver.class);
        when(resolver.resolve(COMMUNITY_ID, USER_ID, START, END, null))
                .thenReturn(new MembershipEnergyMetricsScope(List.of(first), null));
        GetMembershipDailyConsumptionService withoutPeriod =
                new GetMembershipDailyConsumptionServiceImpl(resolver, getDatadisConsumptionService, zoneResolver);

        assertEquals(List.of(), withoutPeriod.getDailySeries(COMMUNITY_ID, USER_ID, START, END));
        verifyNoInteractions(getDatadisConsumptionService);
    }

    private void givenMembership() {
        CommunityMembership membership = CommunityMembershipMother.active()
                .withId(UUID.randomUUID())
                .withUser(UserMother.randomUser())
                .withCommunity(CommunityMother.random().build())
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.of(membership));
    }

    private void givenSupplies(Supply... supplies) {
        when(getSupplyRepository.findAllByOwnerAndCommunityId(argThat(ownerId(USER_ID)), eq(COMMUNITY_ID)))
                .thenReturn(List.of(supplies));
    }

    private static ArgumentMatcher<UserId> ownerId(UUID expected) {
        return actual -> actual != null && expected.equals(actual.getId());
    }

    /**
     * Stubs the per-supply series for exactly these bounds, so a call over any other bounds would
     * return nothing and fail the expectations.
     */
    private void givenDailySeries(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                                  SupplyConsumptionBucket... buckets) {
        when(getDatadisConsumptionService.getDailySeriesBySupply(eq(SupplyId.of(supply.getId())), any(), any()))
                .thenReturn(List.of());
        when(getDatadisConsumptionService.getDailySeriesBySupply(SupplyId.of(supply.getId()), startDate, endDate))
                .thenReturn(List.of(buckets));
    }

    /**
     * A per-supply daily bucket; a null {@code obtainMethod} is how that series reports a day on
     * which the supply stored nothing.
     */
    private static SupplyConsumptionBucket day(String date, float consumptionKWh, float selfConsumptionKWh,
                                               String savingsEur, String obtainMethod) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setDate(date);
        consumption.setTime("00:00");
        consumption.setConsumptionKWh(consumptionKWh);
        consumption.setSurplusEnergyKWh(0f);
        consumption.setGenerationEnergyKWh(selfConsumptionKWh);
        consumption.setSelfConsumptionEnergyKWh(selfConsumptionKWh);
        consumption.setObtainMethod(obtainMethod);
        return SupplyConsumptionBucket.of(consumption,
                SupplySavings.of(new BigDecimal(savingsEur), TariffSource.ESTIMATE, PRICE));
    }

    private static void assertDays(List<MembershipDailyConsumptionBucket> series, String... expected) {
        assertEquals(Arrays.stream(expected).map(LocalDate::parse).toList(),
                series.stream().map(MembershipDailyConsumptionBucket::getDay).toList());
    }

    private static void assertDay(MembershipDailyConsumptionBucket bucket, String consumptionKWh,
                                  String savingsEur, int supplyCount, int suppliesWithData) {
        MembershipConsumptionTotals totals = bucket.getTotals();
        assertDecimal(consumptionKWh, totals.getConsumptionKWh());
        assertDecimal(savingsEur, totals.getSavings().orElseThrow().getAmountEur());
        assertEquals(supplyCount, totals.getSupplyCount(), () -> bucket.getDay() + " supplyCount");
        assertEquals(suppliesWithData, totals.getSuppliesWithData(), () -> bucket.getDay() + " suppliesWithData");
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
