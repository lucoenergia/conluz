package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncDatadisConsumptionsBodyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test
    void theEndDateIsTodayInTheJvmDefaultZone() {
        Instant noon = Instant.parse("2026-11-26T12:00:00Z");
        Clock clock = Clock.fixed(noon, ZoneOffset.UTC);

        assertEquals(LocalDate.ofInstant(noon, ZoneId.systemDefault()),
                new SyncDatadisConsumptionsBody(0).getEndDate(clock));
    }

    @Test
    @DisplayName("DHI-008 monthsBack 0 starts on the first day of the current month")
    void zeroMonthsBackStartsOnTheFirstDayOfTheCurrentMonth() {
        assertEquals(LocalDate.of(2026, 11, 1), new SyncDatadisConsumptionsBody(0).getStartDate(TODAY));
    }

    @Test
    @DisplayName("DHI-008 monthsBack 24 starts on the first day of the month 24 months before today")
    void twentyFourMonthsBackStartsTwoYearsBefore() {
        assertEquals(LocalDate.of(2024, 11, 1), new SyncDatadisConsumptionsBody(24).getStartDate(TODAY));
    }

    @Test
    @DisplayName("DHI-008 the start is the first day of the month, whatever the day of the month today is")
    void theStartIsTheFirstDayOfTheMonthOnAnyDay() {
        // The last day of a month whose month 3 months back is shorter.
        assertEquals(LocalDate.of(2026, 2, 1), new SyncDatadisConsumptionsBody(3).getStartDate(LocalDate.of(2026, 5, 31)));
        // In January, the month 1 month back is in the previous year.
        assertEquals(LocalDate.of(2025, 12, 1), new SyncDatadisConsumptionsBody(1).getStartDate(LocalDate.of(2026, 1, 1)));
    }
}
