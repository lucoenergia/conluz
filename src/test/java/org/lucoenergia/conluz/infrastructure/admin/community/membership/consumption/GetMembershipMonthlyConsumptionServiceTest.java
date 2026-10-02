package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipMonthlyConsumptionService;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipMonthlyConsumptionBucket;
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
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers what the service itself decides: which supplies are read, over which bounds, which months
 * the series has, and which supply's bucket lands in which month. The per-supply monthly series is
 * stubbed; how a month's buckets fold is pinned by {@code MembershipMonthlyConsumptionBucketTest}.
 */
class GetMembershipMonthlyConsumptionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final EstimatedPrice PRICE = EstimatedPrice.of(new BigDecimal("0.15"));

    /** January to April 2024, local: winter time at the start, summer time at the end. */
    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-01T00:00:00+01:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-04-30T23:59:59+02:00");

    private final GetMembershipsRepository getMembershipsRepository = mock(GetMembershipsRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final GetDatadisConsumptionService getDatadisConsumptionService = mock(GetDatadisConsumptionService.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final ReferenceMonthResolver referenceMonthResolver = mock(ReferenceMonthResolver.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);

    private final GetMembershipMonthlyConsumptionService service = new GetMembershipMonthlyConsumptionServiceImpl(
            new MembershipEnergyMetricsScopeResolverImpl(getMembershipsRepository, getSupplyRepository,
                    aggregateRepository, referenceMonthResolver, zoneResolver, new DateConverter(timeConfiguration)),
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
     * AC2. The second supply starts reporting in March. January and February carry the first
     * supply's figures alone, nothing zero-filled for the second, and report it as without data.
     */
    @Test
    void aSupplyStartingMidSeriesContributesOnlyFromItsFirstMonth() {
        givenMembership();
        givenSupplies(first, second);
        givenMonthlySeries(first, START, END,
                month("2024/01/01", 10f, 1f, "0.15", TariffSource.ESTIMATE),
                month("2024/02/01", 20f, 2f, "0.30", TariffSource.ESTIMATE),
                month("2024/03/01", 30f, 3f, "0.45", TariffSource.ESTIMATE),
                month("2024/04/01", 40f, 4f, "0.60", TariffSource.ESTIMATE));
        givenMonthlySeries(second, START, END,
                month("2024/03/01", 100f, 10f, "1.50", TariffSource.ESTIMATE),
                month("2024/04/01", 200f, 20f, "3.00", TariffSource.ESTIMATE));

        List<MembershipMonthlyConsumptionBucket> series = service.getMonthlySeries(COMMUNITY_ID, USER_ID, START, END);

        assertMonths(series, "2024-01", "2024-02", "2024-03", "2024-04");
        assertMonth(series.get(0), "10", "0.15", 2, 1);
        assertMonth(series.get(1), "20", "0.30", 2, 1);
        assertMonth(series.get(2), "130", "1.95", 2, 2);
        assertMonth(series.get(3), "240", "3.60", 2, 2);
    }

    /**
     * AC3. In March one supply is priced with its contracted tariff and the other with the
     * estimate: March is an estimate. February, priced only with the contracted tariff, is not.
     */
    @Test
    void aMonthWhereSuppliesDifferInSourceIsAnEstimate() {
        givenMembership();
        givenSupplies(first, second);
        givenMonthlySeries(first, START, END,
                month("2024/02/01", 10f, 4f, "0.80", TariffSource.REAL_TARIFF),
                month("2024/03/01", 10f, 4f, "0.80", TariffSource.REAL_TARIFF));
        givenMonthlySeries(second, START, END,
                month("2024/03/01", 10f, 2f, "0.30", TariffSource.ESTIMATE));

        List<MembershipMonthlyConsumptionBucket> series = service.getMonthlySeries(COMMUNITY_ID, USER_ID, START, END);

        assertEquals(TariffSource.REAL_TARIFF, series.get(1).getSavings().orElseThrow().getTariffSource());
        assertEquals(TariffSource.ESTIMATE, series.get(2).getSavings().orElseThrow().getTariffSource());
        assertDecimal("1.10", series.get(2).getSavings().orElseThrow().getAmountEur());
    }

    /**
     * AC6. Bounds in the middle of January and of April select February, March and April, the
     * months whose local day-1 midnight falls inside them, as the per-supply series selects its
     * points. March has no record at all and is still emitted, in its place.
     */
    @Test
    void everyMonthSelectedByTheBoundsIsEmittedInOrder() {
        OffsetDateTime start = OffsetDateTime.parse("2024-01-15T12:00:00+01:00");
        OffsetDateTime end = OffsetDateTime.parse("2024-04-10T00:00:00+02:00");
        givenMembership();
        givenSupplies(first);
        givenMonthlySeries(first, start, end,
                month("2024/02/01", 20f, 2f, "0.30", TariffSource.ESTIMATE),
                month("2024/04/01", 40f, 4f, "0.60", TariffSource.ESTIMATE));

        List<MembershipMonthlyConsumptionBucket> series = service.getMonthlySeries(COMMUNITY_ID, USER_ID, start, end);

        assertMonths(series, "2024-02", "2024-03", "2024-04");
        assertMonth(series.get(0), "20", "0.30", 1, 1);
        assertDecimal("0", series.get(1).getConsumptionKWh());
        assertTrue(series.get(1).getSavings().isEmpty());
        assertEquals(0, series.get(1).getSuppliesWithData());
        assertMonth(series.get(2), "40", "0.60", 1, 1);
    }

    /**
     * AC6, the edges of the rule. Both bounds are inclusive, so bounds sitting exactly on local
     * midnight of day 1 select that month. And the bounds are instants: the same wall-clock
     * bounds read in UTC are an hour or two later in Madrid, so 2024-02-01T00:00Z, 01:00 local,
     * leaves February out, while 2024-03-31T23:59:59Z, already 01:59 on 1 April local, takes April
     * in -- one month too few at the start and one too many at the end.
     */
    @Test
    void theBoundsAreInclusiveInstantsComparedWithLocalMidnightOfDayOne() {
        givenMembership();
        givenSupplies();

        assertMonths(service.getMonthlySeries(COMMUNITY_ID, USER_ID,
                        OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                        OffsetDateTime.parse("2024-04-01T00:00:00+02:00")),
                "2024-02", "2024-03", "2024-04");
        assertMonths(service.getMonthlySeries(COMMUNITY_ID, USER_ID,
                        OffsetDateTime.parse("2024-02-01T00:00:00Z"),
                        OffsetDateTime.parse("2024-03-31T23:59:59Z")),
                "2024-03", "2024-04");
    }

    /**
     * AC7. A membership without supplies still gets every requested month, with zero totals, no
     * savings and a supply count of zero; the per-supply series is never asked.
     */
    @Test
    void aMembershipWithoutSuppliesGetsEveryMonthEmpty() {
        givenMembership();
        givenSupplies();

        List<MembershipMonthlyConsumptionBucket> series = service.getMonthlySeries(COMMUNITY_ID, USER_ID, START, END);

        assertMonths(series, "2024-01", "2024-02", "2024-03", "2024-04");
        for (MembershipMonthlyConsumptionBucket bucket : series) {
            assertDecimal("0", bucket.getConsumptionKWh());
            assertTrue(bucket.getSavings().isEmpty());
            assertEquals(0, bucket.getSupplyCount());
            assertEquals(0, bucket.getSuppliesWithData());
        }
        verifyNoInteractions(getDatadisConsumptionService);
    }

    /**
     * AC8. Validated by the shared scope resolver before anything is read.
     */
    @Test
    void aStartAfterTheEndIsRejectedBeforeAnythingIsRead() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> service.getMonthlySeries(COMMUNITY_ID, USER_ID, END, START));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END, exception.getReason());
        verifyNoInteractions(getMembershipsRepository, getSupplyRepository, getDatadisConsumptionService);
    }

    @Test
    void aMembershipThatDoesNotExistIsReportedAsNotFoundWithoutReadingAnything() {
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.empty());

        assertThrows(MembershipNotFoundException.class,
                () -> service.getMonthlySeries(COMMUNITY_ID, USER_ID, START, END));

        verifyNoInteractions(getDatadisConsumptionService);
    }

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
    private void givenMonthlySeries(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                                    SupplyConsumptionBucket... buckets) {
        when(getDatadisConsumptionService.getMonthlySeriesBySupply(eq(SupplyId.of(supply.getId())), any(), any()))
                .thenReturn(List.of());
        when(getDatadisConsumptionService.getMonthlySeriesBySupply(SupplyId.of(supply.getId()), startDate, endDate))
                .thenReturn(List.of(buckets));
    }

    private static SupplyConsumptionBucket month(String date, float consumptionKWh, float selfConsumptionKWh,
                                                 String savingsEur, TariffSource source) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setDate(date);
        consumption.setTime("00:00");
        consumption.setConsumptionKWh(consumptionKWh);
        consumption.setSurplusEnergyKWh(0f);
        consumption.setGenerationEnergyKWh(selfConsumptionKWh);
        consumption.setSelfConsumptionEnergyKWh(selfConsumptionKWh);
        consumption.setObtainMethod("Real");
        EstimatedPrice price = source == TariffSource.ESTIMATE ? PRICE : null;
        return SupplyConsumptionBucket.of(consumption, SupplySavings.of(new BigDecimal(savingsEur), source, price));
    }

    private static void assertMonths(List<MembershipMonthlyConsumptionBucket> series, String... expected) {
        assertEquals(Arrays.stream(expected).map(YearMonth::parse).toList(),
                series.stream().map(MembershipMonthlyConsumptionBucket::getMonth).toList());
    }

    private static void assertMonth(MembershipMonthlyConsumptionBucket bucket, String consumptionKWh,
                                    String savingsEur, int supplyCount, int suppliesWithData) {
        assertDecimal(consumptionKWh, bucket.getConsumptionKWh());
        assertDecimal(savingsEur, bucket.getSavings().orElseThrow().getAmountEur());
        assertEquals(supplyCount, bucket.getSupplyCount(), () -> bucket.getMonth() + " supplyCount");
        assertEquals(suppliesWithData, bucket.getSuppliesWithData(), () -> bucket.getMonth() + " suppliesWithData");
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
