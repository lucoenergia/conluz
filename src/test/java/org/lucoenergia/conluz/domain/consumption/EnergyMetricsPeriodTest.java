package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyMetricsPeriodTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    @DisplayName("EPR-001 AC7 a month runs from midnight of its first day to midnight of the first day of the next")
    void aMonthRunsFromMidnightOfItsFirstDayToMidnightOfTheFirstDayOfTheNext() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofMonth(YearMonth.of(2026, 8), MADRID);

        assertEquals(OffsetDateTime.parse("2026-08-01T00:00:00+02:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-09-01T00:00:00+02:00"), period.getEndDate());
        assertEquals(31 * 24, period.getExpectedHours());
    }

    /**
     * October 2026 leaves summer time on the 25th: its bounds carry different offsets and the
     * month spans one hour more than 31 days of 24 hours.
     */
    @Test
    @DisplayName("EPR-001 each bound of a month carries the offset in force at that instant")
    void eachBoundOfAMonthCarriesTheOffsetInForceAtThatInstant() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofMonth(YearMonth.of(2026, 10), MADRID);

        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+02:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-11-01T00:00:00+01:00"), period.getEndDate());
        assertEquals(31 * 24 + 1, period.getExpectedHours());
    }

    @Test
    void aMonthIsReadInTheGivenZone() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofMonth(YearMonth.of(2026, 8), ZoneId.of("UTC"));

        assertEquals(OffsetDateTime.parse("2026-08-01T00:00:00Z"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-09-01T00:00:00Z"), period.getEndDate());
    }

    @Test
    @DisplayName("EPR-001 AC1 a day runs from its midnight to the next and spans 24 hours")
    void aDaySpansTwentyFourHours() {
        assertEquals(24, new EnergyMetricsPeriod(
                OffsetDateTime.parse("2026-08-10T00:00:00+02:00"),
                OffsetDateTime.parse("2026-08-11T00:00:00+02:00")).getExpectedHours());
    }

    @Test
    @DisplayName("EPR-001 AC1 the October daylight saving day spans 25 hours")
    void theOctoberDaylightSavingDaySpansTwentyFiveHours() {
        assertEquals(25, new EnergyMetricsPeriod(
                OffsetDateTime.parse("2026-10-25T00:00:00+02:00"),
                OffsetDateTime.parse("2026-10-26T00:00:00+01:00")).getExpectedHours());
    }

    @Test
    @DisplayName("EPR-001 AC1 the March daylight saving day spans 23 hours")
    void theMarchDaylightSavingDaySpansTwentyThreeHours() {
        assertEquals(23, new EnergyMetricsPeriod(
                OffsetDateTime.parse("2026-03-29T00:00:00+01:00"),
                OffsetDateTime.parse("2026-03-30T00:00:00+02:00")).getExpectedHours());
    }

    @Test
    @DisplayName("EPR-001 the period of the stored records ends an hour after the last record")
    void thePeriodOfTheStoredRecordsEndsAnHourAfterTheLastRecord() {
        EnergyMetricsPeriod period = EnergyMetricsPeriod.ofRecords(
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T02:00:00+01:00"));

        assertEquals(OffsetDateTime.parse("2024-02-01T00:00:00+01:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2024-02-01T03:00:00+01:00"), period.getEndDate());
        assertEquals(3, period.getExpectedHours());
    }

    @Test
    @DisplayName("EPR-002 an end after the cut-off is moved back to it, the start untouched")
    void anEndAfterTheCutOffIsMovedBackToIt() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-26T00:00:00+01:00");
        OffsetDateTime cutOff = OffsetDateTime.parse("2026-10-26T10:00:00+01:00");

        EnergyMetricsPeriod clipped = new EnergyMetricsPeriod(start, OffsetDateTime.parse("2026-11-01T00:00:00+01:00"))
                .endingNoLaterThan(cutOff);

        assertEquals(start, clipped.getStartDate());
        assertEquals(cutOff, clipped.getEndDate());
        assertEquals(10, clipped.getExpectedHours());
    }

    @Test
    @DisplayName("EPR-002 an end at or before the cut-off is left as it is")
    void anEndAtOrBeforeTheCutOffIsLeftAsItIs() {
        OffsetDateTime cutOff = OffsetDateTime.parse("2026-10-26T10:00:00+01:00");
        EnergyMetricsPeriod atCutOff = new EnergyMetricsPeriod(OffsetDateTime.parse("2026-10-26T00:00:00+01:00"), cutOff);
        EnergyMetricsPeriod before = new EnergyMetricsPeriod(OffsetDateTime.parse("2026-10-26T00:00:00+01:00"),
                OffsetDateTime.parse("2026-10-26T09:00:00+01:00"));

        assertEquals(cutOff, atCutOff.endingNoLaterThan(cutOff).getEndDate());
        assertEquals(before.getEndDate(), before.endingNoLaterThan(cutOff).getEndDate());
    }

    @Test
    @DisplayName("EPR-002 AC4 the current hour starts on the hour in the zone, with the offset in force there")
    void theCurrentHourStartsOnTheHourInTheZone() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T09:30:00Z"), ZoneId.of("UTC"));

        assertEquals(OffsetDateTime.parse("2026-10-26T10:00:00+01:00"),
                EnergyMetricsPeriod.startOfCurrentHour(clock, MADRID));
    }
}
