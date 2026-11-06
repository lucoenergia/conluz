package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityDailyEnergySeriesService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetCommunityDailyEnergySeriesServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    private final CommunityEnergyScopeResolver scopeResolver = mock(CommunityEnergyScopeResolver.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final GetCommunityDailyEnergySeriesService service =
            new GetCommunityDailyEnergySeriesServiceImpl(scopeResolver, aggregateRepository);

    private final Supply first = SupplyMother.random().build();
    private final Supply second = SupplyMother.random().build();
    private final Supply third = SupplyMother.random().build();

    /**
     * 22 of 24 hours is 91.7 %, 21 of 24 is 87.5 %.
     */
    @Test
    @DisplayName("CEM-006 AC7 a day sums the supplies reaching the threshold of its hours, and a day without any is a gap")
    void aDaySumsTheSuppliesReachingTheThresholdAndADayWithoutAnyIsAGap() {
        EnergyMetricsPeriod period = period("2026-05-10T00:00:00+02:00", "2026-05-13T00:00:00+02:00");
        givenScope(period);
        givenDays(first, period, new DailyPublishedAggregate(LocalDate.of(2026, 5, 10), 10d, 1d, 24),
                new DailyPublishedAggregate(LocalDate.of(2026, 5, 11), 7d, 1d, 21));
        givenDays(second, period, new DailyPublishedAggregate(LocalDate.of(2026, 5, 10), 5d, 2d, 22));
        givenDays(third, period);

        List<CommunityEnergySeriesBucket> series = service.getDailySeries(COMMUNITY_ID, period.getStartDate(),
                period.getEndDate());

        assertEquals(3, series.size());
        assertEquals(OffsetDateTime.parse("2026-05-10T00:00:00+02:00"), series.get(0).getStartsAt());
        assertEquals(15d, series.get(0).getSelfConsumptionKWh(), 1e-12);
        assertEquals(3d, series.get(0).getSurplusKWh(), 1e-12);
        assertEquals(2, series.get(0).getSuppliesWithData());
        assertEquals(3, series.get(0).getSupplyCount());
        assertNull(series.get(1).getSelfConsumptionKWh());
        assertEquals(0, series.get(1).getSuppliesWithData());
        assertNull(series.get(2).getSelfConsumptionKWh());
    }

    /**
     * The period starts at 14:00, so the first day has 10 possible hours, of which 9 reach 90 %.
     */
    @Test
    @DisplayName("CEM-006 a day the period starts inside is judged on its hours inside the period")
    void aDayThePeriodStartsInsideIsJudgedOnItsHoursInsideThePeriod() {
        EnergyMetricsPeriod period = period("2026-05-10T14:00:00+02:00", "2026-05-11T00:00:00+02:00");
        givenScope(period);
        givenDays(first, period, new DailyPublishedAggregate(LocalDate.of(2026, 5, 10), 1d, 0d, 9));
        givenDays(second, period, new DailyPublishedAggregate(LocalDate.of(2026, 5, 10), 1d, 0d, 8));
        givenDays(third, period);

        List<CommunityEnergySeriesBucket> series = service.getDailySeries(COMMUNITY_ID, period.getStartDate(),
                period.getEndDate());

        assertEquals(1, series.size());
        assertEquals(1, series.get(0).getSuppliesWithData());
    }

    /**
     * 25 October 2026 lasts 25 hours in Madrid: 22 published hours are 88 % of them.
     */
    @Test
    @DisplayName("CEM-006 the day the clocks go back is judged on its 25 hours")
    void theDayTheClocksGoBackIsJudgedOnItsTwentyFiveHours() {
        EnergyMetricsPeriod period = period("2026-10-25T00:00:00+02:00", "2026-10-26T00:00:00+01:00");
        givenScope(period);
        givenDays(first, period, new DailyPublishedAggregate(LocalDate.of(2026, 10, 25), 1d, 0d, 23));
        givenDays(second, period, new DailyPublishedAggregate(LocalDate.of(2026, 10, 25), 1d, 0d, 22));
        givenDays(third, period);

        assertEquals(1, service.getDailySeries(COMMUNITY_ID, period.getStartDate(), period.getEndDate())
                .get(0).getSuppliesWithData());
    }

    private void givenScope(EnergyMetricsPeriod period) {
        when(scopeResolver.resolve(COMMUNITY_ID, period.getStartDate(), period.getEndDate(), null,
                EnergyMetricsPeriodValidator.MAX_LENGTH))
                .thenReturn(new CommunityEnergyScope(List.of(first, second, third), ZONE, period));
    }

    private void givenDays(Supply supply, EnergyMetricsPeriod period, DailyPublishedAggregate... days) {
        Instant from = period.getStartDate().toInstant();
        Instant to = period.getEndDate().toInstant();
        when(aggregateRepository.aggregateByLocalDay(supply, from, to, ZONE)).thenReturn(List.of(days));
    }

    private static EnergyMetricsPeriod period(String start, String end) {
        return new EnergyMetricsPeriod(OffsetDateTime.parse(start), OffsetDateTime.parse(end));
    }
}
