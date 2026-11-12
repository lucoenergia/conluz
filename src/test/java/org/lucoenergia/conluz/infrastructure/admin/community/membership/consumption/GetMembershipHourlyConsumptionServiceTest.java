package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipHourlyConsumptionService;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipConsumptionTotals;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipHourlyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EstimatedPrice;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplyHourlyConsumptionBucket;
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

class GetMembershipHourlyConsumptionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    /**
     * 11:00 on 27 October 2026 in Madrid, after every period the tests request but the clipped one.
     */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final EstimatedPrice PRICE = EstimatedPrice.of(new BigDecimal("0.15"));

    /** Three local hours, 10:00 to 13:00 on 10 January 2024. */
    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-10T10:00:00+01:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-10T13:00:00+01:00");

    private final GetMembershipsRepository getMembershipsRepository = mock(GetMembershipsRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final GetDatadisConsumptionService getDatadisConsumptionService = mock(GetDatadisConsumptionService.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final ReferenceMonthResolver referenceMonthResolver = mock(ReferenceMonthResolver.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);

    private final GetMembershipHourlyConsumptionService service = new GetMembershipHourlyConsumptionServiceImpl(
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
     * AC1. At 10:00 only the first supply stored a record; at 11:00 both did; the second supply's
     * 10:00 comes back from the per-supply series as a zero bucket without obtainMethod, and is not
     * counted.
     */
    @Test
    @DisplayName("MCS-003 AC1 an hour counts and sums only the supplies with data that hour")
    void anHourCountsAndSumsOnlyTheSuppliesThatStoredARecord() {
        givenMembership();
        givenSupplies(first, second);
        givenHourlySeries(first, START, END,
                hour("2024-01-10T10:00:00+01:00", 10f, 2f, "0.30", "Real"),
                hour("2024-01-10T11:00:00+01:00", 20f, 4f, "0.60", "Real"),
                hour("2024-01-10T12:00:00+01:00", 0f, 0f, "0.00", null));
        givenHourlySeries(second, START, END,
                hour("2024-01-10T10:00:00+01:00", 0f, 0f, "0.00", null),
                hour("2024-01-10T11:00:00+01:00", 5f, 1f, "0.15", "Estimada"),
                hour("2024-01-10T12:00:00+01:00", 0f, 0f, "0.00", null));

        List<MembershipHourlyConsumptionBucket> series = service.getHourlySeries(COMMUNITY_ID, USER_ID, START, END);

        assertHours(series, "2024-01-10T10:00:00+01:00", "2024-01-10T11:00:00+01:00", "2024-01-10T12:00:00+01:00");
        assertHour(series.get(0), "10", "0.30", 2, 1);
        assertHour(series.get(1), "25", "0.75", 2, 2);
        MembershipConsumptionTotals empty = series.get(2).getTotals();
        assertDecimal("0", empty.getConsumptionKWh());
        assertTrue(empty.getSavings().isEmpty());
        assertEquals(2, empty.getSupplyCount());
        assertEquals(0, empty.getSuppliesWithData());
    }

    /**
     * AC3. 25 October 2026, when the clocks go back in Madrid: the per-supply series labels both
     * repeated hours 02:00 of the same date. Matched by the instant they start at, each keeps its own
     * figures; matched by their labels, they would merge.
     */
    @Test
    @DisplayName("MCS-003 AC3 the hours are matched by the instant they start at, not by their local labels")
    void theHoursAreMatchedByTheirStartInstant() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-25T00:00:00+02:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-10-26T00:00:00+01:00");
        givenMembership();
        givenSupplies(first);
        givenHourlySeries(first, start, end,
                hour("2026-10-25T02:00:00+02:00", 1f, 0f, "0.00", "Real"),
                hour("2026-10-25T02:00:00+01:00", 2f, 0f, "0.00", "Real"));

        List<MembershipHourlyConsumptionBucket> series = service.getHourlySeries(COMMUNITY_ID, USER_ID, start, end);

        assertEquals(25, series.size());
        assertEquals(OffsetDateTime.parse("2026-10-25T02:00:00+02:00"), series.get(2).getStartsAt());
        assertDecimal("1", series.get(2).getTotals().getConsumptionKWh());
        assertEquals(OffsetDateTime.parse("2026-10-25T02:00:00+01:00"), series.get(3).getStartsAt());
        assertDecimal("2", series.get(3).getTotals().getConsumptionKWh());
    }

    /**
     * One call to the per-supply series per supply, over the resolved period, whatever the number
     * of hours.
     */
    @Test
    @DisplayName("MCS-003 every supply's hourly series is read once, over the whole period")
    void everySupplysSeriesIsReadOnce() {
        givenMembership();
        givenSupplies(first, second);
        givenHourlySeries(first, START, END);
        givenHourlySeries(second, START, END);

        service.getHourlySeries(COMMUNITY_ID, USER_ID, START, END);

        verify(getDatadisConsumptionService, times(1)).getHourlySeriesBySupply(SupplyId.of(first.getId()), START, END);
        verify(getDatadisConsumptionService, times(1)).getHourlySeriesBySupply(SupplyId.of(second.getId()), START, END);
        verifyNoMoreInteractions(getDatadisConsumptionService);
    }

    /**
     * The clock stands at 11:00 on 27 October 2026 in Madrid. Every supply is read up to that hour,
     * and no hour from it on is emitted though the requested end reaches into November.
     */
    @Test
    @DisplayName("EPR-002 MCS-003 every supply and the hours emitted follow the end clipped to the current hour")
    void everySupplyAndTheHoursEmittedFollowTheClippedEnd() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-27T08:00:00+01:00");
        OffsetDateTime clippedEnd = OffsetDateTime.parse("2026-10-27T11:00:00+01:00");
        givenMembership();
        givenSupplies(first);
        givenHourlySeries(first, start, clippedEnd,
                hour("2026-10-27T10:00:00+01:00", 3f, 0f, "0.00", "Real"));

        List<MembershipHourlyConsumptionBucket> series = service.getHourlySeries(COMMUNITY_ID, USER_ID, start,
                OffsetDateTime.parse("2026-11-03T00:00:00+01:00"));

        assertHours(series, "2026-10-27T08:00:00+01:00", "2026-10-27T09:00:00+01:00", "2026-10-27T10:00:00+01:00");
        assertDecimal("3", series.get(2).getTotals().getConsumptionKWh());
    }

    /**
     * No supplies: every hour, zero totals, no savings, a supply count of zero; the per-supply
     * series is never asked.
     */
    @Test
    @DisplayName("MCS-003 a membership without supplies gets every hour empty")
    void aMembershipWithoutSuppliesGetsEveryHourEmpty() {
        givenMembership();
        givenSupplies();

        List<MembershipHourlyConsumptionBucket> series = service.getHourlySeries(COMMUNITY_ID, USER_ID, START, END);

        assertHours(series, "2024-01-10T10:00:00+01:00", "2024-01-10T11:00:00+01:00", "2024-01-10T12:00:00+01:00");
        for (MembershipHourlyConsumptionBucket bucket : series) {
            assertTrue(bucket.getTotals().getSavings().isEmpty());
            assertEquals(0, bucket.getTotals().getSupplyCount());
            assertEquals(0, bucket.getTotals().getSuppliesWithData());
        }
        verifyNoInteractions(getDatadisConsumptionService);
    }

    @Test
    @DisplayName("EPR-005 MCS-003 a period longer than 31 calendar days is rejected before anything is read")
    void aPeriodLongerThanThirtyOneDaysIsRejectedBeforeAnythingIsRead() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> service.getHourlySeries(COMMUNITY_ID, USER_ID,
                        OffsetDateTime.parse("2024-01-01T00:00:00+01:00"),
                        OffsetDateTime.parse("2024-02-01T01:00:00+01:00")));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.TOO_LONG, exception.getReason());
        verifyNoInteractions(getMembershipsRepository, getSupplyRepository, getDatadisConsumptionService);
    }

    /**
     * Explicit dates always resolve a period, so this branch is not reachable through the
     * resolver. Forced here with a resolver that breaks that invariant: the series fails rather than
     * answering an empty one, as the membership daily series does.
     */
    @Test
    @DisplayName("MCS-003 a scope resolved from explicit bounds without a period is a broken invariant")
    void aScopeWithoutAPeriodIsABrokenInvariant() {
        MembershipEnergyMetricsScopeResolver resolver = mock(MembershipEnergyMetricsScopeResolver.class);
        when(resolver.resolve(COMMUNITY_ID, USER_ID, START, END, null, EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH))
                .thenReturn(new MembershipEnergyMetricsScope(List.of(first), null));
        GetMembershipHourlyConsumptionService withoutPeriod =
                new GetMembershipHourlyConsumptionServiceImpl(resolver, getDatadisConsumptionService, zoneResolver);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> withoutPeriod.getHourlySeries(COMMUNITY_ID, USER_ID, START, END));

        assertTrue(exception.getMessage().contains("without a period"), exception::getMessage);
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
    private void givenHourlySeries(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                                   SupplyHourlyConsumptionBucket... buckets) {
        when(getDatadisConsumptionService.getHourlySeriesBySupply(eq(SupplyId.of(supply.getId())), any(), any()))
                .thenReturn(List.of());
        when(getDatadisConsumptionService.getHourlySeriesBySupply(SupplyId.of(supply.getId()), startDate, endDate))
                .thenReturn(List.of(buckets));
    }

    /**
     * A per-supply hourly bucket, labelled with the local date and time it starts at as that series
     * labels it; a null {@code obtainMethod} is how that series reports an hour in which the supply
     * stored nothing.
     */
    private static SupplyHourlyConsumptionBucket hour(String startsAt, float consumptionKWh,
                                                      float selfConsumptionKWh, String savingsEur,
                                                      String obtainMethod) {
        OffsetDateTime start = OffsetDateTime.parse(startsAt);
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setDate(String.format("%04d/%02d/%02d", start.getYear(), start.getMonthValue(),
                start.getDayOfMonth()));
        consumption.setTime(String.format("%02d:00", start.getHour()));
        consumption.setConsumptionKWh(consumptionKWh);
        consumption.setSurplusEnergyKWh(0f);
        consumption.setGenerationEnergyKWh(selfConsumptionKWh);
        consumption.setSelfConsumptionEnergyKWh(selfConsumptionKWh);
        consumption.setObtainMethod(obtainMethod);
        return SupplyHourlyConsumptionBucket.of(start, SupplyConsumptionBucket.of(consumption,
                SupplySavings.of(new BigDecimal(savingsEur), TariffSource.ESTIMATE, PRICE), obtainMethod != null));
    }

    private static void assertHours(List<MembershipHourlyConsumptionBucket> series, String... expected) {
        assertEquals(Arrays.stream(expected).map(OffsetDateTime::parse).toList(),
                series.stream().map(MembershipHourlyConsumptionBucket::getStartsAt).toList());
    }

    private static void assertHour(MembershipHourlyConsumptionBucket bucket, String consumptionKWh,
                                   String savingsEur, int supplyCount, int suppliesWithData) {
        MembershipConsumptionTotals totals = bucket.getTotals();
        assertDecimal(consumptionKWh, totals.getConsumptionKWh());
        assertDecimal(savingsEur, totals.getSavings().orElseThrow().getAmountEur());
        assertEquals(supplyCount, totals.getSupplyCount(), () -> bucket.getStartsAt() + " supplyCount");
        assertEquals(suppliesWithData, totals.getSuppliesWithData(),
                () -> bucket.getStartsAt() + " suppliesWithData");
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
