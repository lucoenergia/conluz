package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyPublicationTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final YearMonth NOVEMBER_2026 = YearMonth.of(2026, 11);
    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    @Test
    @DisplayName("ENM-001 a month holding 90 % of its hours is published, one hour less is not")
    void aMonthIsPublishedFromNinetyPercentOfItsHours() {
        // June 2026: 720 hours, 90 % of which is 648.
        assertTrue(MonthlyPublication.isPublished(YearMonth.of(2026, 6), LONG_AGO, 648, MADRID));
        assertFalse(MonthlyPublication.isPublished(YearMonth.of(2026, 6), LONG_AGO, 647, MADRID));
    }

    /**
     * October 2024 lasts 745 hours in Madrid, the clocks going back: 743 of them is far above 90 %.
     */
    @Test
    @DisplayName("ENM-001 a month of 745 hours holding 743 of them is published")
    void aMonthWithTheClocksGoingBackHoldingTwoHoursLessIsPublished() {
        assertTrue(MonthlyPublication.isPublished(YearMonth.of(2024, 10), LONG_AGO, 743, MADRID));
    }

    /**
     * March 2025 lasts 743 hours in Madrid, the clocks going forward; 669 is the first count above 668.7.
     */
    @Test
    @DisplayName("ENM-001 a month of 743 hours is judged against 743, not 744")
    void aMonthWithTheClocksGoingForwardIsJudgedAgainstItsOwnHours() {
        assertTrue(MonthlyPublication.isPublished(YearMonth.of(2025, 3), LONG_AGO, 743, MADRID));
        assertTrue(MonthlyPublication.isPublished(YearMonth.of(2025, 3), LONG_AGO, 669, MADRID));
        assertFalse(MonthlyPublication.isPublished(YearMonth.of(2025, 3), LONG_AGO, 668, MADRID));
    }

    /**
     * From 15 August 2026 at local midnight to the end of the month: 17 days, 408 hours.
     */
    @Test
    @DisplayName("ENM-001 a month is judged from the first record when it falls inside the month")
    void aMonthIsJudgedFromTheFirstRecordInsideIt() {
        Instant firstRecord = Instant.parse("2026-08-14T22:00:00Z");

        assertTrue(MonthlyPublication.isPublished(YearMonth.of(2026, 8), firstRecord, 408, MADRID));
        assertFalse(MonthlyPublication.isPublished(YearMonth.of(2026, 8), firstRecord, 367, MADRID));
    }

    @Test
    @DisplayName("ENM-002 the first candidate is 24 months before the current one")
    void theFirstCandidateIsTwentyFourMonthsBeforeTheCurrentOne() {
        assertEquals(YearMonth.of(2024, 11), MonthlyPublication.firstCandidate(NOVEMBER_2026));
    }

    @Test
    @DisplayName("ENM-002 the latest published month is the latest before the current one that reaches the threshold")
    void theLatestPublishedMonthIsTheLatestReachingTheThreshold() {
        Map<YearMonth, Long> hours = Map.of(
                YearMonth.of(2026, 11), 400L,
                YearMonth.of(2026, 10), 300L,
                YearMonth.of(2026, 9), 720L,
                YearMonth.of(2026, 8), 744L);

        assertEquals(Optional.of(YearMonth.of(2026, 9)),
                MonthlyPublication.latestPublished(hours, LONG_AGO, NOVEMBER_2026, MADRID));
    }

    @Test
    @DisplayName("ENM-002 the current month is never a candidate, however complete")
    void theCurrentMonthIsNeverACandidate() {
        Map<YearMonth, Long> hours = Map.of(NOVEMBER_2026, 720L);

        assertEquals(Optional.empty(), MonthlyPublication.latestPublished(hours, LONG_AGO, NOVEMBER_2026, MADRID));
    }

    @Test
    @DisplayName("ENM-002 a month older than the 24 months before the current one is not resolved")
    void aMonthOutsideTheWindowIsNotResolved() {
        Map<YearMonth, Long> hours = Map.of(
                YearMonth.of(2024, 10), 745L,
                YearMonth.of(2024, 11), 720L);

        assertEquals(Optional.of(YearMonth.of(2024, 11)),
                MonthlyPublication.latestPublished(hours, LONG_AGO, NOVEMBER_2026, MADRID));
        assertEquals(Optional.empty(), MonthlyPublication.latestPublished(Map.of(YearMonth.of(2024, 10), 745L),
                LONG_AGO, NOVEMBER_2026, MADRID));
    }

    /**
     * Before its first record a month has no possible hour, so, counted as one, it would reach the threshold with
     * none.
     */
    @Test
    @DisplayName("ENM-001 a month before the first record is never resolved")
    void aMonthBeforeTheFirstRecordIsNeverResolved() {
        Instant firstRecord = Instant.parse("2026-11-02T10:00:00Z");

        assertEquals(Optional.empty(), MonthlyPublication.latestPublished(Map.of(), firstRecord, NOVEMBER_2026,
                MADRID));
    }

    @Test
    @DisplayName("ENM-001 the month of the first record is judged from that record")
    void theMonthOfTheFirstRecordIsJudgedFromIt() {
        Instant firstRecord = Instant.parse("2026-10-30T23:00:00Z");

        assertEquals(Optional.of(YearMonth.of(2026, 10)), MonthlyPublication.latestPublished(
                Map.of(YearMonth.of(2026, 10), 24L), firstRecord, NOVEMBER_2026, MADRID));
    }
}
