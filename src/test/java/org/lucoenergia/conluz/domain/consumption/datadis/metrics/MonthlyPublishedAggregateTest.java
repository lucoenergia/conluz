package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyPublishedAggregateTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Test
    void theDaysOfEachMonthAreAddedUpAndTheLastObtainMethodIsKept() {
        Map<YearMonth, MonthlyPublishedAggregate> months = MonthlyPublishedAggregate.foldIntoMonths(List.of(
                new DailyPublishedAggregate(LocalDate.of(2026, 6, 1), 1d, 2d, 3d, 24, "Real"),
                new DailyPublishedAggregate(LocalDate.of(2026, 6, 30), 10d, 20d, 30d, 20, "Estimated"),
                new DailyPublishedAggregate(LocalDate.of(2026, 7, 1), 5d, 0d, 1d, 0, null)));

        assertEquals(List.of(JUNE, JULY), List.copyOf(months.keySet()));
        assertEquals(new MonthlyPublishedAggregate(JUNE, 11d, 22d, 33d, 44, "Estimated"), months.get(JUNE));
        assertEquals(new MonthlyPublishedAggregate(JULY, 5d, 0d, 1d, 0, null), months.get(JULY));
    }

    @Test
    void aDayWithoutObtainMethodDoesNotEraseAnEarlierOne() {
        Map<YearMonth, MonthlyPublishedAggregate> months = MonthlyPublishedAggregate.foldIntoMonths(List.of(
                new DailyPublishedAggregate(LocalDate.of(2026, 6, 1), 1d, 1d, 0d, 24, "Real"),
                new DailyPublishedAggregate(LocalDate.of(2026, 6, 2), 1d, 1d, 0d, 24, null)));

        assertEquals("Real", months.get(JUNE).obtainMethod());
    }

    @Test
    void ENM_001_aMonthAtExactlyNinetyPercentOfItsHoursIsPublishedAndOneHourLessIsNot() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofMonth(JUNE, MADRID);
        // June has 720 hours; 90 % of them is 648.
        assertTrue(month(JUNE, 648).isPublishedWithin(period, MADRID));
        assertFalse(month(JUNE, 647).isPublishedWithin(period, MADRID));
    }

    @Test
    void theThresholdCountsOnlyTheMonthsHoursInsideThePeriod() {
        // The period ends on 11 June at midnight: 240 of June's hours, 90 % of which is 216.
        EnergyMetricsPeriod period = new EnergyMetricsPeriod(OffsetDateTime.parse("2026-06-01T00:00:00+02:00"),
                OffsetDateTime.parse("2026-06-11T00:00:00+02:00"));

        assertTrue(month(JUNE, 216).isPublishedWithin(period, MADRID));
        assertFalse(month(JUNE, 215).isPublishedWithin(period, MADRID));
    }

    @Test
    void aMonthWithoutPublishedHoursIsNeverPublished() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofMonth(JUNE, MADRID);

        assertFalse(month(JUNE, 0).isPublishedWithin(period, MADRID));
    }

    private static MonthlyPublishedAggregate month(YearMonth month, long publishedHours) {
        return new MonthlyPublishedAggregate(month, 1d, 1d, 1d, publishedHours, "Real");
    }
}
