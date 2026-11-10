package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyMetricsPeriodValidatorTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-07-01T00:00:00+02:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-08-01T00:00:00+02:00");
    private static final EnergyMetricsReferencePeriod LATEST = EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH;

    /**
     * 2026-10-26T10:30 in Madrid, the day after the clocks went back.
     */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T09:30:00Z"), ZoneId.of("UTC"));
    private static final OffsetDateTime CURRENT_HOUR = EnergyMetricsPeriod.startOfCurrentHour(CLOCK, MADRID);

    @Test
    void theCurrentHourStartsOnTheHourInThePeriodsZone() {
        assertEquals(OffsetDateTime.parse("2026-10-26T10:00:00+01:00"), CURRENT_HOUR);
    }

    @Test
    void aReferencePeriodTogetherWithBothDatesIsConflicting() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.CONFLICTING_PERIOD, START, END, LATEST);
    }

    /**
     * Conflicting is reported before incomplete: the caller asked for two different ways of
     * resolving the period, which is the mistake to fix first.
     */
    @Test
    void aReferencePeriodTogetherWithASingleDateIsConflicting() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.CONFLICTING_PERIOD, START, null, LATEST);
        assertReason(InvalidEnergyMetricsPeriodException.Reason.CONFLICTING_PERIOD, null, END, LATEST);
    }

    @Test
    void aReferencePeriodAloneIsValid() {
        assertDoesNotThrow(() -> validate(null, null, LATEST));
    }

    @Test
    void theDateRulesStillApplyWithoutAReferencePeriod() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.INCOMPLETE_PERIOD, START, null, null);
        assertReason(InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END, END, START, null);
        assertDoesNotThrow(() -> validate(START, END, null));
        assertDoesNotThrow(() -> validate(null, null, null));
    }

    @Test
    @DisplayName("EPR-001 AC3 equal bounds make an empty period and are rejected as a start not before the end")
    void equalBoundsAreRejected() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END, START, START, null);
    }

    // --- EPR-003: a start at or after the start of the current hour ---

    @Test
    @DisplayName("EPR-003 AC5 a start at the start of the current hour is rejected")
    void aStartAtTheStartOfTheCurrentHourIsRejected() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.START_IN_FUTURE,
                OffsetDateTime.parse("2026-10-26T10:00:00+01:00"), OffsetDateTime.parse("2026-11-01T00:00:00+01:00"),
                null);
    }

    @Test
    @DisplayName("EPR-003 AC5 a start tomorrow is rejected")
    void aStartTomorrowIsRejected() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.START_IN_FUTURE,
                OffsetDateTime.parse("2026-10-27T00:00:00+01:00"), OffsetDateTime.parse("2026-10-28T00:00:00+01:00"),
                null);
    }

    @Test
    @DisplayName("EPR-003 AC5 a start in the previous hour is accepted")
    void aStartInThePreviousHourIsAccepted() {
        assertDoesNotThrow(() -> validate(OffsetDateTime.parse("2026-10-26T09:00:00+01:00"),
                OffsetDateTime.parse("2026-11-01T00:00:00+01:00"), null));
    }

    /**
     * The rules are reported in order: an inverted period in the future is a start not before the
     * end, not a start in the future.
     */
    @Test
    @DisplayName("EPR-001 EPR-003 an inverted future period is reported as a start not before its end")
    void anInvertedFuturePeriodIsReportedAsAStartNotBeforeItsEnd() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END,
                OffsetDateTime.parse("2026-12-02T00:00:00+01:00"), OffsetDateTime.parse("2026-12-01T00:00:00+01:00"),
                null);
    }

    // --- EPR-004: at most 24 calendar months, on the requested bounds ---

    @Test
    @DisplayName("EPR-004 AC6 exactly 24 calendar months is accepted")
    void exactlyTwentyFourMonthsIsAccepted() {
        assertDoesNotThrow(() -> validate(OffsetDateTime.parse("2024-10-01T00:00:00+02:00"),
                OffsetDateTime.parse("2026-10-01T00:00:00+02:00"), null));
    }

    @Test
    @DisplayName("EPR-004 AC6 24 calendar months and a day is rejected")
    void twentyFourMonthsAndADayIsRejected() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.TOO_LONG,
                OffsetDateTime.parse("2024-10-01T00:00:00+02:00"), OffsetDateTime.parse("2026-10-02T00:00:00+02:00"),
                null);
    }

    /**
     * Clipping the end to the current hour would bring this period under 24 months, but the cap
     * is evaluated on the bounds as requested.
     */
    @Test
    @DisplayName("EPR-004 AC6 the cap is evaluated on the requested end, even when it is in the future")
    void theCapIsEvaluatedOnTheRequestedEnd() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.TOO_LONG,
                OffsetDateTime.parse("2024-10-27T00:00:00+02:00"), OffsetDateTime.parse("2026-10-28T00:00:00+01:00"),
                null);
    }

    /**
     * Calendar months in the period's zone, not in UTC: 31 March 2024 began in winter time and 31
     * March 2026 begins in summer time, so the local midnight 24 months later is an hour earlier
     * than the same UTC time 24 months later.
     */
    @Test
    @DisplayName("EPR-004 the 24 calendar months are counted in the period's time zone")
    void theMonthsAreCountedInThePeriodsZone() {
        OffsetDateTime start = OffsetDateTime.parse("2024-03-31T00:00:00+01:00");

        assertDoesNotThrow(() -> validate(start, OffsetDateTime.parse("2026-03-31T00:00:00+02:00"), null));
        assertReason(InvalidEnergyMetricsPeriodException.Reason.TOO_LONG,
                start, OffsetDateTime.parse("2026-03-31T00:30:00+02:00"), null);
    }

    @Test
    @DisplayName("EPR-004 a refusal for length carries the 24 calendar months of the default cap")
    void aRefusalForLengthCarriesTheDefaultCap() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> validate(OffsetDateTime.parse("2024-10-01T00:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-02T00:00:00+02:00"), null));
        assertEquals(Period.ofMonths(24), exception.getMaxLength());
    }

    /**
     * An operation can pass a cap of its own: the period is held to it instead of the default, and
     * the refusal carries it.
     */
    @Test
    void aCapOfTheOperationReplacesTheDefault() {
        OffsetDateTime start = OffsetDateTime.parse("2026-07-01T00:00:00+02:00");
        Period week = Period.ofDays(7);

        assertDoesNotThrow(() -> EnergyMetricsPeriodValidator.validate(start,
                OffsetDateTime.parse("2026-07-08T00:00:00+02:00"), CURRENT_HOUR, MADRID, week));
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> EnergyMetricsPeriodValidator.validate(start, OffsetDateTime.parse("2026-07-08T01:00:00+02:00"),
                        CURRENT_HOUR, MADRID, week));
        assertEquals(InvalidEnergyMetricsPeriodException.Reason.TOO_LONG, exception.getReason());
        assertEquals(week, exception.getMaxLength());
    }

    @Test
    void aRefusalForAnotherReasonCarriesNoCap() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> validate(END, START, null));
        assertEquals(null, exception.getMaxLength());
    }

    // --- EPR-005: an hourly series is at most 31 calendar days, on the requested bounds ---

    @Test
    @DisplayName("EPR-005 exactly 31 calendar days are accepted for an hourly series")
    void exactlyThirtyOneDaysAreAcceptedForAnHourlySeries() {
        assertDoesNotThrow(() -> validateHourly(OffsetDateTime.parse("2026-09-01T00:00:00+02:00"),
                OffsetDateTime.parse("2026-10-02T00:00:00+02:00")));
    }

    @Test
    @DisplayName("EPR-005 31 calendar days and one hour are rejected for an hourly series, carrying the 31 days")
    void thirtyOneDaysAndAnHourAreRejectedForAnHourlySeries() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> validateHourly(OffsetDateTime.parse("2026-09-01T00:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-02T01:00:00+02:00")));
        assertEquals(InvalidEnergyMetricsPeriodException.Reason.TOO_LONG, exception.getReason());
        assertEquals(Period.ofDays(31), exception.getMaxLength());
    }

    /**
     * October 2026 in Madrid is 31 calendar days of 745 hours: a cap of 744 hours would reject it.
     */
    @Test
    @DisplayName("EPR-005 the 31 calendar days are counted in the period's time zone")
    void theThirtyOneDaysAreCountedInThePeriodsZone() {
        assertDoesNotThrow(() -> validateHourly(OffsetDateTime.parse("2026-09-26T00:00:00+02:00"),
                OffsetDateTime.parse("2026-10-27T00:00:00+01:00")));
        assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> validateHourly(OffsetDateTime.parse("2026-09-26T00:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-27T01:00:00+01:00")));
    }

    private static void validateHourly(OffsetDateTime startDate, OffsetDateTime endDate) {
        EnergyMetricsPeriodValidator.validate(startDate, endDate, CURRENT_HOUR, MADRID,
                EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH);
    }

    private static void validate(OffsetDateTime startDate, OffsetDateTime endDate,
                                 EnergyMetricsReferencePeriod referencePeriod) {
        EnergyMetricsPeriodValidator.validate(startDate, endDate, referencePeriod, CURRENT_HOUR, MADRID);
    }

    @Test
    void aRequestedReferencePeriodTogetherWithADateIsConflicting() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> EnergyMetricsPeriodValidator.validate(START, null, true, CURRENT_HOUR, MADRID,
                        EnergyMetricsPeriodValidator.MAX_LENGTH));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.CONFLICTING_PERIOD, exception.getReason());
    }

    @Test
    void aRequestedReferencePeriodAloneIsAccepted() {
        assertDoesNotThrow(() -> EnergyMetricsPeriodValidator.validate(null, null, true, CURRENT_HOUR, MADRID,
                EnergyMetricsPeriodValidator.MAX_LENGTH));
    }

    @Test
    @DisplayName("EPR-005 without a reference period, the dates are checked against the given length")
    void withoutAReferencePeriodTheDatesAreCheckedAgainstTheGivenLength() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> EnergyMetricsPeriodValidator.validate(START, START.plusDays(32), false, CURRENT_HOUR, MADRID,
                        EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.TOO_LONG, exception.getReason());
    }

    private static void assertReason(InvalidEnergyMetricsPeriodException.Reason reason, OffsetDateTime startDate,
                                     OffsetDateTime endDate, EnergyMetricsReferencePeriod referencePeriod) {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> validate(startDate, endDate, referencePeriod));
        assertEquals(reason, exception.getReason());
    }
}
