package org.lucoenergia.conluz.domain.price.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceSyncPeriodValidatorTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-04-01");

    @ParameterizedTest(name = "[{0}, {1}) is accepted")
    @CsvSource({
            // a single day
            "2026-03-28, 2026-03-29",
            // exactly 31 days
            "2026-02-01, 2026-03-04",
            // up to tomorrow, so ending the day after tomorrow
            "2026-03-28, 2026-04-03",
            // tomorrow alone
            "2026-04-02, 2026-04-03"
    })
    @DisplayName("PRC-005 a range of at most 31 days extending no further than tomorrow is accepted")
    void acceptsAValidRange(LocalDate startDate, LocalDate endDate) {
        assertDoesNotThrow(() -> PriceSyncPeriodValidator.validate(startDate, endDate, TODAY));
    }

    @ParameterizedTest(name = "[{0}, {1}) is rejected with {2}")
    @CsvSource({
            // equal bounds make an empty range
            "2026-03-28, 2026-03-28, START_AFTER_END",
            "2026-03-29, 2026-03-28, START_AFTER_END",
            // 32 days
            "2026-02-01, 2026-03-05, TOO_LONG",
            // one day beyond tomorrow
            "2026-03-28, 2026-04-04, END_BEYOND_TOMORROW",
            "2026-04-03, 2026-04-04, END_BEYOND_TOMORROW"
    })
    @DisplayName("PRC-005 an empty, inverted, too long or beyond-tomorrow range is rejected")
    void rejectsAnInvalidRange(LocalDate startDate, LocalDate endDate, InvalidPriceSyncPeriodException.Reason reason) {
        InvalidPriceSyncPeriodException exception = assertThrows(InvalidPriceSyncPeriodException.class,
                () -> PriceSyncPeriodValidator.validate(startDate, endDate, TODAY));

        assertEquals(reason, exception.getReason());
    }

    @Test
    @DisplayName("PRC-005 an inverted range is reported as such before its length or its end")
    void checksTheOrderFirst() {
        InvalidPriceSyncPeriodException exception = assertThrows(InvalidPriceSyncPeriodException.class,
                () -> PriceSyncPeriodValidator.validate(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-01-01"),
                        TODAY));

        assertEquals(InvalidPriceSyncPeriodException.Reason.START_AFTER_END, exception.getReason());
    }

    @Test
    @DisplayName("PRC-005 a too long range is reported as such before its end")
    void checksTheLengthBeforeTheEnd() {
        InvalidPriceSyncPeriodException exception = assertThrows(InvalidPriceSyncPeriodException.class,
                () -> PriceSyncPeriodValidator.validate(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-06-01"),
                        TODAY));

        assertEquals(InvalidPriceSyncPeriodException.Reason.TOO_LONG, exception.getReason());
    }
}
