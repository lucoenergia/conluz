package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeTest {

    private static final LocalDate START = LocalDate.of(2025, 3, 10);
    private static final LocalDate END = LocalDate.of(2025, 3, 14);

    @Test
    void nullStartIsRejected() {
        InvalidDateRangeException exception =
                assertThrows(InvalidDateRangeException.class, () -> new DateRange(null, END));

        assertEquals(InvalidDateRangeException.Reason.NULL_START, exception.getReason());
    }

    @Test
    void nullEndIsRejected() {
        InvalidDateRangeException exception =
                assertThrows(InvalidDateRangeException.class, () -> new DateRange(START, null));

        assertEquals(InvalidDateRangeException.Reason.NULL_END, exception.getReason());
    }

    @Test
    void startAfterEndIsRejected() {
        InvalidDateRangeException exception =
                assertThrows(InvalidDateRangeException.class, () -> new DateRange(END, START));

        assertEquals(InvalidDateRangeException.Reason.START_AFTER_END, exception.getReason());
    }

    @Test
    void startEqualToEndIsRejected() {
        InvalidDateRangeException exception =
                assertThrows(InvalidDateRangeException.class, () -> new DateRange(START, START));

        assertEquals(InvalidDateRangeException.Reason.EMPTY_RANGE, exception.getReason());
    }

    @Test
    void aRangeOfAtLeastOneDayIsAccepted() {
        DateRange range = new DateRange(START, START.plusDays(1));

        assertEquals(START, range.getStart());
        assertEquals(START.plusDays(1), range.getEnd());
    }

    @Test
    void containsIncludesTheStartDate() {
        assertTrue(new DateRange(START, END).contains(START));
    }

    @Test
    void containsExcludesTheEndDate() {
        assertFalse(new DateRange(START, END).contains(END));
    }

    @Test
    void containsIncludesTheDayBeforeTheEndDate() {
        assertTrue(new DateRange(START, END).contains(END.minusDays(1)));
    }

    @Test
    void containsExcludesTheDayBeforeTheStartDate() {
        assertFalse(new DateRange(START, END).contains(START.minusDays(1)));
    }

    @Test
    void containsIncludesADateInTheMiddle() {
        assertTrue(new DateRange(START, END).contains(START.plusDays(2)));
    }
}
