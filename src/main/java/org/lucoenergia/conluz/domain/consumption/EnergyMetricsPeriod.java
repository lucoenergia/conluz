package org.lucoenergia.conluz.domain.consumption;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A resolved period of energy metrics. Both bounds are inclusive: a period ending on a day ends on
 * the last hourly record of that day rather than on the following midnight.
 */
public class EnergyMetricsPeriod {

    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;

    public EnergyMetricsPeriod(OffsetDateTime startDate, OffsetDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * The first instant of the period, inclusive.
     */
    public OffsetDateTime getStartDate() {
        return startDate;
    }

    /**
     * The last instant of the period, inclusive.
     */
    public OffsetDateTime getEndDate() {
        return endDate;
    }

    /**
     * The number of hourly slots the period spans, both bounds inclusive. Counting the elapsed
     * time between two instants that carry their offset is what makes a day with a daylight
     * saving transition come out as 23 or 25 hours instead of 24.
     */
    public long getExpectedHours() {
        return ChronoUnit.HOURS.between(startDate, endDate) + 1;
    }
}
