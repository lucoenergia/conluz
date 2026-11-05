package org.lucoenergia.conluz.domain.consumption;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
     * This period, ending no later than {@code cutOff}: an end after it is moved back to it, so the
     * period never reaches an hour that has not fully happened. The start is left as it is.
     */
    public EnergyMetricsPeriod endingNoLaterThan(OffsetDateTime cutOff) {
        return endDate.isAfter(cutOff) ? new EnergyMetricsPeriod(startDate, cutOff) : this;
    }

    /**
     * Every hour that starts inside the period, in chronological order: from the first whole hour at
     * or after the start -- the store stamps every record at the start of its hour, so an hour that
     * started before the period holds nothing the period read -- up to the last one starting before
     * the end.
     *
     * <p>The hours are counted on the instant line, not on the local clock, which is what makes a
     * spring-forward day 23 hours long and a fall-back day 25, its repeated hour included twice.
     *
     * @param zone the zone whose whole hours are counted
     */
    public List<Instant> hoursStartingIn(ZoneId zone) {
        Instant from = startDate.toInstant();
        Instant toExclusive = endDate.toInstant();
        Instant hour = from.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant();
        if (hour.isBefore(from)) {
            hour = hour.plus(1, ChronoUnit.HOURS);
        }
        List<Instant> hours = new ArrayList<>();
        for (; hour.isBefore(toExclusive); hour = hour.plus(1, ChronoUnit.HOURS)) {
            hours.add(hour);
        }
        return hours;
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
