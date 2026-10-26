package org.lucoenergia.conluz.domain.consumption;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * A resolved period of energy metrics. Both bounds are inclusive: a period ending on a day ends on
 * the last hourly record of that day rather than on the following midnight.
 */
public class EnergyMetricsPeriod {

    /**
     * The hour of the last hourly record of a day. Both period bounds are inclusive, so a period
     * ending on a day ends on this hour rather than on the following midnight.
     */
    private static final LocalTime LAST_HOUR_OF_DAY = LocalTime.of(23, 0);

    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;

    public EnergyMetricsPeriod(OffsetDateTime startDate, OffsetDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * A calendar month read in {@code zone}: from the first hour of the month to its last, both
     * inclusive, each carrying the offset in force at that instant.
     */
    public static EnergyMetricsPeriod ofMonth(YearMonth month, ZoneId zone) {
        return new EnergyMetricsPeriod(
                month.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                month.atEndOfMonth().atTime(LAST_HOUR_OF_DAY).atZone(zone).toOffsetDateTime());
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
