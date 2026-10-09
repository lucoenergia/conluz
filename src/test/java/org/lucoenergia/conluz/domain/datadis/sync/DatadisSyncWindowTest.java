package org.lucoenergia.conluz.domain.datadis.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatadisSyncWindowTest {

    @Test
    void theDefaultFirstDayIsTheFirstOfTheMonthOneYearBack() {
        assertEquals(LocalDate.of(2025, 10, 1), DatadisSyncWindow.DEFAULT.firstDay(LocalDate.of(2026, 10, 9)));
        assertEquals(LocalDate.of(2025, 2, 1), DatadisSyncWindow.DEFAULT.firstDay(LocalDate.of(2026, 2, 28)));
    }

    @Test
    @DisplayName("DCA-001 the default window covers 13 months: the month the sync starts in through the current one")
    void theDefaultWindowCoversThirteenMonths() {
        List<YearMonth> months = DatadisSyncWindow.DEFAULT.months(LocalDate.of(2026, 10, 9));

        assertEquals(13, months.size());
        assertEquals(YearMonth.of(2025, 10), months.get(0));
        assertEquals(YearMonth.of(2026, 10), months.get(12));
        for (int i = 1; i < months.size(); i++) {
            assertEquals(months.get(i - 1).plusMonths(1), months.get(i), "months are consecutive, oldest first");
        }
    }

    @Test
    @DisplayName("DCA-001 a lookback of N months yields N + 1 months, both ends inclusive")
    void aLookbackOfNMonthsYieldsNPlusOneMonths() {
        DatadisSyncWindow window = new DatadisSyncWindow(Period.ofMonths(3));

        assertEquals(List.of(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9),
                        YearMonth.of(2026, 10)),
                window.months(LocalDate.of(2026, 10, 31)));
    }

    @Test
    @DisplayName("DCA-001 the window starts in the month of the sync's first day, whatever the day of the month")
    void theWindowStartsInTheMonthOfTheFirstDay() {
        DatadisSyncWindow window = new DatadisSyncWindow(Period.ofDays(45));

        // 2026-10-20 minus 45 days is 2026-09-05: September is the first month the sync re-reads.
        assertEquals(List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
                window.months(LocalDate.of(2026, 10, 20)));
        // 2026-10-01 minus 45 days is 2026-08-17.
        assertEquals(List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
                window.months(LocalDate.of(2026, 10, 1)));
    }

    @Test
    void aZeroLookbackCoversTheCurrentMonthOnly() {
        assertEquals(List.of(YearMonth.of(2026, 10)),
                new DatadisSyncWindow(Period.ZERO).months(LocalDate.of(2026, 10, 9)));
    }

    @Test
    @DisplayName("DCA-003 the years of the window are every year its months overlap, oldest first")
    void theYearsAreEveryYearTheMonthsOverlap() {
        assertEquals(List.of(2025, 2026), DatadisSyncWindow.DEFAULT.years(LocalDate.of(2026, 10, 9)));
        assertEquals(List.of(2025, 2026), DatadisSyncWindow.DEFAULT.years(LocalDate.of(2026, 1, 1)));
        assertEquals(List.of(2025, 2026), new DatadisSyncWindow(Period.ofMonths(3)).years(LocalDate.of(2026, 2, 15)));
        assertEquals(List.of(2026), new DatadisSyncWindow(Period.ofMonths(3)).years(LocalDate.of(2026, 10, 9)));
    }

    @Test
    void aNegativeLookbackIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DatadisSyncWindow(Period.ofMonths(-1)));
    }
}
