package org.lucoenergia.conluz.domain.consumption;

import java.time.OffsetDateTime;

/**
 * The rules an explicitly requested energy metrics period must satisfy, shared by every endpoint
 * that accepts one so that all of them reject the same requests for the same reasons.
 */
public final class EnergyMetricsPeriodValidator {

    private EnergyMetricsPeriodValidator() {
    }

    /**
     * Both dates are optional and must be supplied together, and the start cannot be after the
     * end.
     *
     * @throws InvalidEnergyMetricsPeriodException when exactly one date is supplied, or the start
     *         is after the end
     */
    public static void validate(OffsetDateTime startDate, OffsetDateTime endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.INCOMPLETE_PERIOD);
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END);
        }
    }

    /**
     * As {@link #validate(OffsetDateTime, OffsetDateTime)}, for an endpoint that can also resolve a
     * reference period itself: requesting one together with either date is rejected first.
     *
     * @param referencePeriod the reference period requested, or null when none was
     * @throws InvalidEnergyMetricsPeriodException when a reference period is requested together
     *         with a date, or when the dates themselves are invalid
     */
    public static void validate(OffsetDateTime startDate, OffsetDateTime endDate,
                                EnergyMetricsReferencePeriod referencePeriod) {
        if (referencePeriod != null && (startDate != null || endDate != null)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.CONFLICTING_PERIOD);
        }
        validate(startDate, endDate);
    }
}
