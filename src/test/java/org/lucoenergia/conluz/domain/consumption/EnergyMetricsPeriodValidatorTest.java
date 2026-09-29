package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyMetricsPeriodValidatorTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-07-01T00:00:00+02:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-07-31T23:00:00+02:00");
    private static final EnergyMetricsReferencePeriod LATEST = EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH;

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
        assertDoesNotThrow(() -> EnergyMetricsPeriodValidator.validate(null, null, LATEST));
    }

    @Test
    void theDateRulesStillApplyWithoutAReferencePeriod() {
        assertReason(InvalidEnergyMetricsPeriodException.Reason.INCOMPLETE_PERIOD, START, null, null);
        assertReason(InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END, END, START, null);
        assertDoesNotThrow(() -> EnergyMetricsPeriodValidator.validate(START, END, null));
        assertDoesNotThrow(() -> EnergyMetricsPeriodValidator.validate(null, null, null));
    }

    private static void assertReason(InvalidEnergyMetricsPeriodException.Reason reason, OffsetDateTime startDate,
                                     OffsetDateTime endDate, EnergyMetricsReferencePeriod referencePeriod) {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> EnergyMetricsPeriodValidator.validate(startDate, endDate, referencePeriod));
        assertEquals(reason, exception.getReason());
    }
}
