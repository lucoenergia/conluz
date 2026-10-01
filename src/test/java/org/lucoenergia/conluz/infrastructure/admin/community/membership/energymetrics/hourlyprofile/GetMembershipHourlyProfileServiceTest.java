package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.hourlyprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.GetMembershipHourlyProfileService;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.HourlyProfileBucket;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.MembershipHourlyProfile;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisHourlyRecordsRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.HourlyEnergyRecord;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers what the service itself decides: which month is read, that every supply is read over it
 * into one profile, and where coverage comes from. The averaging rules are pinned by
 * {@code HourlyProfileAccumulatorTest}.
 */
class GetMembershipHourlyProfileServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static final OffsetDateTime JULY_START = OffsetDateTime.parse("2026-07-01T00:00:00+02:00");
    private static final OffsetDateTime JULY_END = OffsetDateTime.parse("2026-07-31T23:00:00+02:00");

    private final MembershipEnergyMetricsScopeResolver scopeResolver = mock(MembershipEnergyMetricsScopeResolver.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final GetDatadisHourlyRecordsRepository recordsRepository = mock(GetDatadisHourlyRecordsRepository.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);

    private final GetMembershipHourlyProfileService service = new GetMembershipHourlyProfileServiceImpl(
            scopeResolver, aggregateRepository, recordsRepository, zoneResolver);

    private final Supply first = SupplyMother.random().build();
    private final Supply second = SupplyMother.random().build();

    @BeforeEach
    void givenTheCommunityZone() {
        when(zoneResolver.resolveZoneIdForCommunity(COMMUNITY_ID)).thenReturn(ZONE);
    }

    @Test
    void aMembershipThatDoesNotExistIsReportedAsNotFoundWithoutReadingAnything() {
        when(scopeResolver.resolveLatestPublishedMonth(COMMUNITY_ID, USER_ID))
                .thenThrow(new MembershipNotFoundException(COMMUNITY_ID, USER_ID));

        assertThrows(MembershipNotFoundException.class, () -> service.getHourlyProfile(COMMUNITY_ID, USER_ID));

        verifyNoInteractions(aggregateRepository, recordsRepository);
    }

    @Test
    void withoutAResolvedMonthNothingIsReadAndEveryBucketIsEmpty() {
        givenScope(null, first, second);

        MembershipHourlyProfile profile = service.getHourlyProfile(COMMUNITY_ID, USER_ID);

        assertNull(profile.getStartDate());
        assertNull(profile.getEndDate());
        assertEquals(24, profile.getBuckets().size());
        profile.getBuckets().forEach(bucket -> {
            assertNull(bucket.getAverageConsumptionKWh());
            assertNull(bucket.getAverageAssignedProductionKWh());
        });
        assertEquals(2, profile.getCoverage().getSupplyCount());
        assertEquals(0L, profile.getCoverage().getExpectedHours());
        verifyNoInteractions(aggregateRepository, recordsRepository);
    }

    /**
     * Both supplies are read over the resolved month and folded into the same buckets, so the
     * average divides by the records of both: (10 + 10 + 2) / 3, not (10 + 2) / 2.
     */
    @Test
    void everySupplyIsReadOverTheResolvedMonthIntoOneProfile() {
        givenScope(new EnergyMetricsPeriod(JULY_START, JULY_END), first, second);
        givenHoursWithData(first, 2L);
        givenHoursWithData(second, 1L);
        givenRecords(first,
                record("2026-07-01T08:00:00Z", 6d, 4d, 2d),
                record("2026-07-02T08:00:00Z", 6d, 4d, 2d));
        givenRecords(second, record("2026-07-01T08:00:00Z", 1d, 1d, 1d));

        MembershipHourlyProfile profile = service.getHourlyProfile(COMMUNITY_ID, USER_ID);

        assertEquals(JULY_START, profile.getStartDate());
        assertEquals(JULY_END, profile.getEndDate());
        HourlyProfileBucket ten = profile.getBuckets().get(10);
        assertEquals(22d / 3, ten.getAverageConsumptionKWh(), 1e-9);
        assertEquals(3L, ten.getConsumptionSampleCount());
        assertEquals(14d / 3, ten.getAverageAssignedProductionKWh(), 1e-9);
    }

    /**
     * Coverage comes from the aggregate count, like the aggregated energy metrics, and the expected
     * hours are the month's 744 per supply.
     */
    @Test
    void coverageIsCountedByTheAggregateQueryOverEverySupply() {
        givenScope(new EnergyMetricsPeriod(JULY_START, JULY_END), first, second);
        givenHoursWithData(first, 700L);
        givenHoursWithData(second, 0L);
        givenRecords(first);
        givenRecords(second);

        MembershipHourlyProfile profile = service.getHourlyProfile(COMMUNITY_ID, USER_ID);

        assertEquals(700L, profile.getCoverage().getHoursWithData());
        assertEquals(2 * 744L, profile.getCoverage().getExpectedHours());
        assertEquals(2, profile.getCoverage().getSupplyCount());
        assertEquals(1, profile.getCoverage().getSuppliesWithData());
    }

    // --- helpers ---

    private void givenScope(EnergyMetricsPeriod period, Supply... supplies) {
        when(scopeResolver.resolveLatestPublishedMonth(COMMUNITY_ID, USER_ID))
                .thenReturn(new MembershipEnergyMetricsScope(List.of(supplies), period));
    }

    private void givenHoursWithData(Supply supply, long hoursWithData) {
        when(aggregateRepository.aggregateByRangeOfDates(supply, JULY_START, JULY_END))
                .thenReturn(new DatadisConsumptionAggregate(0d, 0d, 0d, hoursWithData));
    }

    @SuppressWarnings("unchecked")
    private void givenRecords(Supply supply, HourlyEnergyRecord... records) {
        doAnswer(invocation -> {
            Consumer<HourlyEnergyRecord> consumer = invocation.getArgument(3);
            for (HourlyEnergyRecord record : records) {
                consumer.accept(record);
            }
            return null;
        }).when(recordsRepository).forEachRecord(eq(supply), eq(JULY_START), eq(JULY_END), any(Consumer.class));
    }

    private static HourlyEnergyRecord record(String time, Double gridImportKWh, Double selfConsumptionKWh,
                                             Double surplusKWh) {
        return new HourlyEnergyRecord(Instant.parse(time), gridImportKWh, selfConsumptionKWh, surplusKWh);
    }
}
