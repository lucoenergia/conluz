package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyMetricsPeriodTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void aMonthRunsFromTheFirstHourOfItsFirstDayToTheLastHourOfItsLastDay() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofMonth(YearMonth.of(2026, 8), MADRID);

        assertEquals(OffsetDateTime.parse("2026-08-01T00:00:00+02:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-08-31T23:00:00+02:00"), period.getEndDate());
        assertEquals(31 * 24, period.getExpectedHours());
    }

    /**
     * October 2026 leaves summer time on the 25th: its bounds carry different offsets and the
     * month spans one hour more than 31 days of 24 hours.
     */
    @Test
    void eachBoundOfAMonthCarriesTheOffsetInForceAtThatInstant() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofMonth(YearMonth.of(2026, 10), MADRID);

        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+02:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-10-31T23:00:00+01:00"), period.getEndDate());
        assertEquals(31 * 24 + 1, period.getExpectedHours());
    }

    @Test
    void aMonthIsReadInTheGivenZone() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofMonth(YearMonth.of(2026, 8), ZoneId.of("UTC"));

        assertEquals(OffsetDateTime.parse("2026-08-01T00:00:00Z"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-08-31T23:00:00Z"), period.getEndDate());
    }
}
