package org.lucoenergia.conluz.domain.consumption;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * The rules an explicitly requested energy metrics period must satisfy, shared by every endpoint
 * that accepts one so that all of them reject the same requests for the same reasons, in the same
 * order.
 */
public final class EnergyMetricsPeriodValidator {

    /**
     * The longest period that may be requested, in calendar months.
     */
    public static final int MAX_MONTHS = 24;

    private EnergyMetricsPeriodValidator() {
    }

    /**
     * Both dates are optional and must be supplied together. When they are, the start must be
     * strictly before the end, since the period is half-open and equal bounds make an empty one;
     * the start must be before the start of the current hour, since a period that has not begun
     * holds nothing; and the end must be no later than {@link #MAX_MONTHS} calendar months after
     * the start, counted in {@code zone} on the bounds as requested.
     *
     * @param startOfCurrentHour the start of the current hour in the period's zone, read once by
     *                           the caller so that it can clip the period against the same instant
     * @param zone               the zone the period's calendar months are counted in
     * @throws InvalidEnergyMetricsPeriodException when the first of those rules is broken
     */
    public static void validate(OffsetDateTime startDate, OffsetDateTime endDate,
                                OffsetDateTime startOfCurrentHour, ZoneId zone) {
        if ((startDate == null) != (endDate == null)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.INCOMPLETE_PERIOD);
        }
        if (startDate == null) {
            return;
        }
        if (!startDate.isBefore(endDate)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END);
        }
        if (!startDate.isBefore(startOfCurrentHour)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.START_IN_FUTURE);
        }
        if (endDate.isAfter(startDate.atZoneSameInstant(zone).plusMonths(MAX_MONTHS).toOffsetDateTime())) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.TOO_LONG);
        }
    }

    /**
     * As {@link #validate(OffsetDateTime, OffsetDateTime, OffsetDateTime, ZoneId)}, for an endpoint
     * that can also resolve a reference period itself: requesting one together with either date is
     * rejected first.
     *
     * @param referencePeriod the reference period requested, or null when none was
     * @throws InvalidEnergyMetricsPeriodException when a reference period is requested together
     *         with a date, or when the dates themselves are invalid
     */
    public static void validate(OffsetDateTime startDate, OffsetDateTime endDate,
                                EnergyMetricsReferencePeriod referencePeriod,
                                OffsetDateTime startOfCurrentHour, ZoneId zone) {
        if (referencePeriod != null && (startDate != null || endDate != null)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.CONFLICTING_PERIOD);
        }
        validate(startDate, endDate, startOfCurrentHour, zone);
    }
}
