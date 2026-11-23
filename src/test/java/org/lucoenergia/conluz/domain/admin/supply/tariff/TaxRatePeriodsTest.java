package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.supply.tariff.InvalidTaxRatePeriodsException.Reason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxRatePeriodsTest {

    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.21");

    private static final TaxRatePeriod FIRST = period("2024-01-01", "2024-03-01", "0.10");
    private static final TaxRatePeriod SECOND = period("2024-03-01", "2024-07-01", "0.21");
    private static final TaxRatePeriod THIRD = period("2024-07-01", "2025-01-01", "0.10");
    private static final TaxRatePeriod LAST_OPEN = period("2025-01-01", null, "0.05");

    private static TaxRatePeriod period(String from, String to, String rate) {
        return new TaxRatePeriod(
                from == null ? null : LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to),
                rate == null ? null : new BigDecimal(rate));
    }

    private static DateRange range(String start, String end) {
        return new DateRange(LocalDate.parse(start), LocalDate.parse(end));
    }

    @Test
    @DisplayName("TRF-004 ordered, contiguous periods with only the last open-ended are accepted")
    void orderedContiguousPeriodsAreAccepted() {
        TaxRatePeriods periods = new TaxRatePeriods(List.of(FIRST, SECOND, THIRD, LAST_OPEN), DEFAULT_RATE);

        assertEquals(List.of(FIRST, SECOND, THIRD, LAST_OPEN), periods.getPeriods());
        assertEquals(DEFAULT_RATE, periods.getDefaultRate());
    }

    @Test
    void aRateOfZeroIsAccepted() {
        TaxRatePeriods periods = new TaxRatePeriods(
                List.of(period("2024-01-01", null, "0")), BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(periods.rateOn(LocalDate.parse("2024-06-01"))));
    }

    @Test
    void mutatingTheSourceListAfterConstructionDoesNotAlterThePeriods() {
        List<TaxRatePeriod> source = new ArrayList<>(List.of(FIRST, SECOND));

        TaxRatePeriods periods = new TaxRatePeriods(source, DEFAULT_RATE);
        source.clear();

        assertEquals(List.of(FIRST, SECOND), periods.getPeriods());
    }

    @ParameterizedTest(name = "TRF-004 {0} is rejected")
    @MethodSource("invalidPeriods")
    void invalidPeriodsAreRejected(String label, List<TaxRatePeriod> periods, BigDecimal defaultRate,
                                   Reason expectedReason, Integer expectedIndex) {
        InvalidTaxRatePeriodsException exception = assertThrows(InvalidTaxRatePeriodsException.class,
                () -> new TaxRatePeriods(periods, defaultRate));

        assertEquals(expectedReason, exception.getReason());
        assertEquals(expectedIndex, exception.getPeriodIndex());
    }

    private static Stream<Arguments> invalidPeriods() {
        return Stream.of(
                Arguments.of("a period without a start", List.of(FIRST, period(null, "2024-07-01", "0.21")),
                        DEFAULT_RATE, Reason.FROM_MISSING, 1),
                Arguments.of("a period without a rate", List.of(FIRST, period("2024-03-01", "2024-07-01", null)),
                        DEFAULT_RATE, Reason.RATE_MISSING, 1),
                Arguments.of("a period ending on its start", List.of(FIRST, period("2024-03-01", "2024-03-01", "0.21")),
                        DEFAULT_RATE, Reason.PERIOD_EMPTY, 1),
                Arguments.of("a period ending before its start",
                        List.of(FIRST, period("2024-03-01", "2024-02-01", "0.21")),
                        DEFAULT_RATE, Reason.PERIOD_EMPTY, 1),
                Arguments.of("unordered periods", List.of(SECOND, FIRST, THIRD),
                        DEFAULT_RATE, Reason.UNORDERED, 1),
                Arguments.of("overlapping periods", List.of(FIRST, period("2024-02-15", "2024-07-01", "0.21"), THIRD),
                        DEFAULT_RATE, Reason.OVERLAP, 1),
                Arguments.of("a gap between periods", List.of(FIRST, period("2024-03-15", "2024-07-01", "0.21"), THIRD),
                        DEFAULT_RATE, Reason.GAP, 1),
                Arguments.of("an open-ended period that is not the last",
                        List.of(FIRST, period("2024-03-01", null, "0.21"), THIRD),
                        DEFAULT_RATE, Reason.OPEN_ENDED_NOT_LAST, 1),
                Arguments.of("a rate of exactly one", List.of(FIRST, period("2024-03-01", "2024-07-01", "1")),
                        DEFAULT_RATE, Reason.RATE_OUT_OF_RANGE, 1),
                Arguments.of("a negative rate", List.of(FIRST, period("2024-03-01", "2024-07-01", "-0.01")),
                        DEFAULT_RATE, Reason.RATE_OUT_OF_RANGE, 1),
                Arguments.of("a missing default rate", List.of(FIRST, SECOND),
                        null, Reason.DEFAULT_RATE_MISSING, null),
                Arguments.of("a default rate of exactly one", List.of(FIRST, SECOND),
                        BigDecimal.ONE, Reason.DEFAULT_RATE_OUT_OF_RANGE, null),
                Arguments.of("a negative default rate", List.of(FIRST, SECOND),
                        new BigDecimal("-0.21"), Reason.DEFAULT_RATE_OUT_OF_RANGE, null)
        );
    }

    @Test
    void rateOnIsTheRateOfThePeriodCoveringTheDate() {
        TaxRatePeriods periods = new TaxRatePeriods(List.of(FIRST, SECOND, THIRD, LAST_OPEN), DEFAULT_RATE);

        assertEquals(new BigDecimal("0.10"), periods.rateOn(LocalDate.parse("2024-02-29")));
        // A period's start is included and its end excluded.
        assertEquals(new BigDecimal("0.21"), periods.rateOn(LocalDate.parse("2024-03-01")));
        assertEquals(new BigDecimal("0.21"), periods.rateOn(LocalDate.parse("2024-06-30")));
        assertEquals(new BigDecimal("0.10"), periods.rateOn(LocalDate.parse("2024-07-01")));
        assertEquals(new BigDecimal("0.05"), periods.rateOn(LocalDate.parse("2030-01-01")));
    }

    @Test
    @DisplayName("TRF-003 a date before the first period, or after a closed last one, is at the default rate")
    void rateOnAnUncoveredDateIsTheDefaultRate() {
        TaxRatePeriods periods = new TaxRatePeriods(List.of(FIRST, SECOND, THIRD), DEFAULT_RATE);

        assertEquals(DEFAULT_RATE, periods.rateOn(LocalDate.parse("2023-12-31")));
        assertEquals(DEFAULT_RATE, periods.rateOn(LocalDate.parse("2025-01-01")));
    }

    @Test
    void boundariesWithinAreTheStartsAndEndsStrictlyInsideTheRange() {
        TaxRatePeriods periods = new TaxRatePeriods(List.of(FIRST, SECOND, THIRD, LAST_OPEN), DEFAULT_RATE);

        assertEquals(List.of(LocalDate.parse("2024-03-01"), LocalDate.parse("2024-07-01")),
                List.copyOf(periods.boundariesWithin(range("2024-02-01", "2024-08-01"))));
        // The range's own bounds are never boundaries within it.
        assertEquals(List.of(), List.copyOf(periods.boundariesWithin(range("2024-03-01", "2024-07-01"))));
        assertEquals(List.of(LocalDate.parse("2024-01-01")),
                List.copyOf(periods.boundariesWithin(range("2023-12-01", "2024-02-01"))));
    }

    @Test
    void uncoveredWithinIsThePartBeforeTheFirstPeriodAndAfterAClosedLastOne() {
        TaxRatePeriods periods = new TaxRatePeriods(List.of(FIRST, SECOND, THIRD), DEFAULT_RATE);

        assertEquals(List.of(range("2023-12-01", "2024-01-01"), range("2025-01-01", "2025-02-01")),
                periods.uncoveredWithin(range("2023-12-01", "2025-02-01")));
        assertEquals(List.of(range("2023-11-01", "2023-12-01")),
                periods.uncoveredWithin(range("2023-11-01", "2023-12-01")));
        assertEquals(List.of(), periods.uncoveredWithin(range("2024-02-01", "2024-08-01")));
    }

    @Test
    void anOpenEndedLastPeriodLeavesNothingUncoveredAfterIt() {
        TaxRatePeriods periods = new TaxRatePeriods(List.of(FIRST, SECOND, THIRD, LAST_OPEN), DEFAULT_RATE);

        assertEquals(List.of(), periods.uncoveredWithin(range("2026-11-01", "2026-12-01")));
    }

    @Test
    void withNoPeriodTheWholeRangeIsUncoveredAndAtTheDefaultRate() {
        TaxRatePeriods periods = new TaxRatePeriods(List.of(), DEFAULT_RATE);

        assertEquals(List.of(range("2024-01-01", "2024-02-01")),
                periods.uncoveredWithin(range("2024-01-01", "2024-02-01")));
        assertEquals(DEFAULT_RATE, periods.rateOn(LocalDate.parse("2024-01-15")));
    }
}
