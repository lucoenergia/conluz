package org.lucoenergia.conluz.domain.production.huawei.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HuaweiSyncWindowTest {

    @Test
    void theDefaultLookbackIsSevenDays() {
        assertEquals(Period.ofDays(7), HuaweiSyncWindow.DEFAULT.lookback());
    }

    @Test
    void theFirstDayIsTheLookbackBeforeToday() {
        assertEquals(LocalDate.of(2026, 10, 12), HuaweiSyncWindow.DEFAULT.firstDay(LocalDate.of(2026, 10, 19)));
        assertEquals(LocalDate.of(2026, 2, 22), HuaweiSyncWindow.DEFAULT.firstDay(LocalDate.of(2026, 3, 1)));
    }

    @Test
    @DisplayName("HPA-003 a window inside one month covers that month only")
    void aWindowInsideOneMonthCoversThatMonthOnly() {
        assertEquals(List.of(YearMonth.of(2026, 10)), HuaweiSyncWindow.DEFAULT.months(LocalDate.of(2026, 10, 19)));
        // 2026-10-08 minus 7 days is 2026-10-01: the first synced day is still October.
        assertEquals(List.of(YearMonth.of(2026, 10)), HuaweiSyncWindow.DEFAULT.months(LocalDate.of(2026, 10, 8)));
    }

    @Test
    @DisplayName("HPA-003 a window across a month boundary covers the previous and the current month")
    void aWindowAcrossAMonthBoundaryCoversBothMonths() {
        List<YearMonth> septemberAndOctober = List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10));

        // A run on the 3rd: the window starts on 2026-09-26.
        assertEquals(septemberAndOctober, HuaweiSyncWindow.DEFAULT.months(LocalDate.of(2026, 10, 3)));
        // A run on the 1st, after the previous month's last day has been synced.
        assertEquals(septemberAndOctober, HuaweiSyncWindow.DEFAULT.months(LocalDate.of(2026, 10, 1)));
        // The last day whose window still reaches back to 30 September.
        assertEquals(septemberAndOctober, HuaweiSyncWindow.DEFAULT.months(LocalDate.of(2026, 10, 7)));
    }

    @Test
    @DisplayName("HPA-003 on 1 January the window covers December and January, and both years")
    void onTheFirstOfJanuaryTheWindowSpansTwoYears() {
        LocalDate newYear = LocalDate.of(2027, 1, 1);

        assertEquals(List.of(YearMonth.of(2026, 12), YearMonth.of(2027, 1)), HuaweiSyncWindow.DEFAULT.months(newYear));
        assertEquals(List.of(2026, 2027), HuaweiSyncWindow.DEFAULT.years(newYear));
        assertEquals(List.of(2026), HuaweiSyncWindow.DEFAULT.years(LocalDate.of(2026, 10, 3)));
    }

    @Test
    @DisplayName("HPA-003 the months follow the sync window lookback, with no second value to change")
    void theMonthsFollowTheLookback() {
        // 2026-10-19 minus 40 days is 2026-09-09.
        assertEquals(List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
                new HuaweiSyncWindow(Period.ofDays(40)).months(LocalDate.of(2026, 10, 19)));
        assertEquals(List.of(YearMonth.of(2026, 10)), new HuaweiSyncWindow(Period.ZERO).months(LocalDate.of(2026, 10, 1)));
    }

    @Test
    void aNegativeLookbackIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HuaweiSyncWindow(Period.ofDays(-1)));
    }
}
