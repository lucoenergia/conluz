package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityMonthlyEnergySeriesService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetCommunityMonthlyEnergySeriesServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    /** From mid-July to October: August and September are selected. */
    private static final EnergyMetricsPeriod PERIOD = new EnergyMetricsPeriod(
            OffsetDateTime.parse("2026-07-15T00:00:00+02:00"), OffsetDateTime.parse("2026-10-01T00:00:00+02:00"));
    private static final Instant FROM = PERIOD.getStartDate().toInstant();
    private static final Instant TO = PERIOD.getEndDate().toInstant();

    private final CommunityEnergyScopeResolver scopeResolver = mock(CommunityEnergyScopeResolver.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final GetCommunityMonthlyEnergySeriesService service =
            new GetCommunityMonthlyEnergySeriesServiceImpl(scopeResolver, aggregateRepository);

    private final Supply first = SupplyMother.random().build();
    private final Supply second = SupplyMother.random().build();

    /**
     * August spans 744 hours, September 720: 670 of 744 and 648 of 720 reach 90 %, 669 and 647 do not.
     * A day of July, before the first selected month, belongs to no bucket.
     */
    @Test
    @DisplayName("CEM-006 CEM-004 a month sums the supplies reaching 90 % of its hours, read in a single query")
    void aMonthSumsTheSuppliesReachingTheThresholdReadInASingleQuery() {
        givenScope(first, second);
        when(aggregateRepository.aggregateByLocalDay(List.of(first, second), FROM, TO, ZONE)).thenReturn(Map.of(
                first.getCode(), days(YearMonth.of(2026, 8), 670, 1.5, 0.5, YearMonth.of(2026, 9), 647, 1d, 1d),
                second.getCode(), days(YearMonth.of(2026, 8), 669, 2d, 2d, YearMonth.of(2026, 9), 648, 3d, 0d)));

        List<CommunityEnergySeriesBucket> series = service.getMonthlySeries(COMMUNITY_ID, PERIOD.getStartDate(),
                PERIOD.getEndDate());

        assertEquals(2, series.size());
        assertEquals(OffsetDateTime.parse("2026-08-01T00:00:00+02:00"), series.get(0).getStartsAt());
        assertEquals(1.5, series.get(0).getSelfConsumptionKWh(), 1e-9);
        assertEquals(0.5, series.get(0).getSurplusKWh(), 1e-9);
        assertEquals(1, series.get(0).getSuppliesWithData());
        assertEquals(2, series.get(0).getSupplyCount());
        assertEquals(3d, series.get(1).getSelfConsumptionKWh(), 1e-9);
        assertEquals(1, series.get(1).getSuppliesWithData());
        verify(aggregateRepository, times(1)).aggregateByLocalDay(List.of(first, second), FROM, TO, ZONE);
        verify(aggregateRepository, never()).aggregateByLocalDay(any(Supply.class), any(), any(), any());
    }

    @Test
    @DisplayName("CEM-006 a month without any supply with data is a gap, and published zeros are a measured zero")
    void aMonthWithoutDataIsAGapAndPublishedZerosAreAMeasuredZero() {
        givenScope(first, second);
        when(aggregateRepository.aggregateByLocalDay(List.of(first, second), FROM, TO, ZONE)).thenReturn(Map.of(
                first.getCode(), days(YearMonth.of(2026, 9), 720, 0d, 0d)));

        List<CommunityEnergySeriesBucket> series = service.getMonthlySeries(COMMUNITY_ID, PERIOD.getStartDate(),
                PERIOD.getEndDate());

        assertNull(series.get(0).getSelfConsumptionKWh());
        assertNull(series.get(0).getSurplusKWh());
        assertEquals(0, series.get(0).getSuppliesWithData());
        assertEquals(0d, series.get(1).getSelfConsumptionKWh(), 0d);
        assertEquals(0d, series.get(1).getSurplusKWh(), 0d);
        assertEquals(1, series.get(1).getSuppliesWithData());
    }

    /**
     * The period ends on 11 September: its possible hours are the 240 of its first ten days.
     */
    @Test
    @DisplayName("CEM-006 a month the period ends inside is judged on its hours inside the period")
    void aMonthThePeriodEndsInsideIsJudgedOnItsHoursInsideThePeriod() {
        EnergyMetricsPeriod period = new EnergyMetricsPeriod(OffsetDateTime.parse("2026-09-01T00:00:00+02:00"),
                OffsetDateTime.parse("2026-09-11T00:00:00+02:00"));
        when(scopeResolver.resolve(COMMUNITY_ID, period.getStartDate(), period.getEndDate(), null,
                EnergyMetricsPeriodValidator.MAX_LENGTH))
                .thenReturn(new CommunityEnergyScope(List.of(first, second), ZONE, period));
        when(aggregateRepository.aggregateByLocalDay(List.of(first, second), period.getStartDate().toInstant(),
                period.getEndDate().toInstant(), ZONE)).thenReturn(Map.of(
                first.getCode(), List.of(new DailyPublishedAggregate(LocalDate.of(2026, 9, 1), 1d, 0d, 216)),
                second.getCode(), List.of(new DailyPublishedAggregate(LocalDate.of(2026, 9, 1), 1d, 0d, 215))));

        List<CommunityEnergySeriesBucket> series = service.getMonthlySeries(COMMUNITY_ID, period.getStartDate(),
                period.getEndDate());

        assertEquals(1, series.get(0).getSuppliesWithData());
    }

    private void givenScope(Supply... supplies) {
        when(scopeResolver.resolve(COMMUNITY_ID, PERIOD.getStartDate(), PERIOD.getEndDate(), null,
                EnergyMetricsPeriodValidator.MAX_LENGTH))
                .thenReturn(new CommunityEnergyScope(List.of(supplies), ZONE, PERIOD));
    }

    /**
     * The given published hours of each month spread over its first days, 24 to a day, with the totals
     * on the first day; plus a July day the period reaches but no selected month holds.
     */
    private static List<DailyPublishedAggregate> days(Object... monthsHoursAndTotals) {
        List<DailyPublishedAggregate> days = new ArrayList<>();
        days.add(new DailyPublishedAggregate(LocalDate.of(2026, 7, 20), 100d, 100d, 24));
        for (int i = 0; i < monthsHoursAndTotals.length; i += 4) {
            YearMonth month = (YearMonth) monthsHoursAndTotals[i];
            int hours = (Integer) monthsHoursAndTotals[i + 1];
            double selfConsumptionKWh = (Double) monthsHoursAndTotals[i + 2];
            double surplusKWh = (Double) monthsHoursAndTotals[i + 3];
            for (int day = 0; hours > 0; day++, hours -= 24) {
                days.add(new DailyPublishedAggregate(month.atDay(day + 1), day == 0 ? selfConsumptionKWh : 0d,
                        day == 0 ? surplusKWh : 0d, Math.min(24, hours)));
            }
        }
        return days;
    }
}
