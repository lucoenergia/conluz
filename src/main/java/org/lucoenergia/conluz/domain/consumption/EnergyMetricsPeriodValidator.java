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
}
