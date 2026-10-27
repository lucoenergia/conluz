package org.lucoenergia.conluz.domain.consumption;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A resolved period of energy metrics, half-open: {@code [startDate, endDate)}. A record sitting
 * exactly on the start counts, one sitting exactly on the end does not, so a day ends on the
 * following midnight and adjacent periods never share a record.
 */
public class EnergyMetricsPeriod {

    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;

    public EnergyMetricsPeriod(OffsetDateTime startDate, OffsetDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * A calendar month read in {@code zone}: from local midnight of its first day to local midnight
     * of the first day of the next month, each bound carrying the offset in force at that instant.
     */
    public static EnergyMetricsPeriod ofMonth(YearMonth month, ZoneId zone) {
        return new EnergyMetricsPeriod(
                month.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime());
    }

    /**
     * The period the stored hourly records span: from the first record to the end of the hour of
     * the last one, so the last record is still inside the half-open period.
     */
    public static EnergyMetricsPeriod ofRecords(OffsetDateTime firstRecord, OffsetDateTime lastRecord) {
        return new EnergyMetricsPeriod(firstRecord, lastRecord.plusHours(1));
    }

    /**
     * The start of the hour still in progress on {@code clock}, read in {@code zone} and carrying
     * the offset in force there: no hour from it onwards has fully happened yet.
     */
    public static OffsetDateTime startOfCurrentHour(Clock clock, ZoneId zone) {
        return ZonedDateTime.now(clock).withZoneSameInstant(zone).truncatedTo(ChronoUnit.HOURS)
                .toOffsetDateTime();
    }

    /**
     * The first instant of the period, inclusive.
     */
    public OffsetDateTime getStartDate() {
        return startDate;
    }

    /**
     * The end of the period, exclusive: the first instant that is no longer part of it.
     */
    public OffsetDateTime getEndDate() {
        return endDate;
    }

    /**
     * The number of hourly slots the period spans. Counting the elapsed time between two instants
     * that carry their offset is what makes a day with a daylight saving transition come out as 23
     * or 25 hours instead of 24.
     */
    public long getExpectedHours() {
        return ChronoUnit.HOURS.between(startDate, endDate);
    }
}
