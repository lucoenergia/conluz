package org.lucoenergia.conluz.infrastructure.shared.time;

import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class DateConverter {

    public static final String DATE_FORMAT = "yyyy/MM/dd";
    public static final String TIME_FORMAT = "HH:mm";

    private final TimeConfiguration timeConfiguration;

    public DateConverter(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    public int getYearFromStringDate(String dateString) {
        // Try full date format first (most common case from hourly consumptions)
        try {
            DateTimeFormatter fullDateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
            return LocalDate.parse(dateString, fullDateFormatter).getYear();
        } catch (DateTimeParseException e) {
            // Fall back to year-month format for backward compatibility
            try {
                DateTimeFormatter yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy/MM");
                return YearMonth.parse(dateString, yearMonthFormatter).getYear();
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                    String.format("Unable to extract year from date string '%s'. Expected format 'yyyy/MM/dd' or 'yyyy/MM'",
                        dateString), ex);
            }
        }
    }

    public long convertStringDateToMilliseconds(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm");

        ZoneId zoneId = timeConfiguration.getZoneId();

        LocalDateTime dateTime = LocalDateTime.parse(dateString, formatter);
        return dateTime.atZone(zoneId).toInstant().toEpochMilli();
    }

    public OffsetDateTime convertInstantToOffsetDateTime(Instant instant) {

        ZoneId zoneId = timeConfiguration.getZoneId();

        ZonedDateTime zonedDateTime = instant.atZone(zoneId);

        return instant.atOffset(zonedDateTime.getOffset());
    }

    public static LocalDate convertStringToLocalDate(String dateString) {
        return convertStringToLocalDate(dateString, DATE_FORMAT);
    }

    /**
     * Converts an API-level <em>inclusive</em> end into the exclusive upper bound the half-open
     * query and segment machinery uses uniformly. No series in this system is sub-second, so
     * nudging by one nanosecond is a lossless conversion rather than an approximation: no record
     * can fall strictly between the inclusive end and the value returned here.
     *
     * <p>Callers must apply this <strong>once</strong>, at the top of the service, and pass the
     * resulting instant to every downstream call, so the fetched record set and the
     * segment-covered instant set are the same set by construction.
     *
     * <p>{@link #convertToString(Instant)} formats nine fractional digits, so the added nanosecond
     * survives into the query literal rather than being truncated away.
     */
    public static Instant toExclusiveUpperBound(OffsetDateTime inclusiveEnd) {
        return inclusiveEnd.toInstant().plusNanos(1);
    }

    public static LocalDate convertStringToLocalDate(String dateString, String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDate.parse(dateString, formatter);
    }

    public OffsetDateTime convertMillisecondsToOffsetDateTime(long milliseconds) {
        return convertMillisecondsToInstant(milliseconds).atZone(ZoneOffset.UTC).toOffsetDateTime();
    }

    public Instant convertMillisecondsToInstant(long milliseconds) {
        return Instant.ofEpochMilli(milliseconds);
    }

    public OffsetDateTime now() {
        return timeConfiguration.now();
    }

    public String convertToString(OffsetDateTime time) {
        // Convert to UTC by extracting the Instant
        return convertToString(time.toInstant());
    }

    public String convertToString(Instant instant) {
        // Define a DateTimeFormatter for InfluxDB timestamp format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'");
        // Format the Instant to the desired InfluxDB timestamp format
        return instant.atZone(ZoneOffset.UTC).format(formatter);
    }

    public String convertToLastDayOfTheMonthAsString(Month month, int year) {
        LocalDate localDate = LocalDate.of(year, month, 1);

        return String.format("%s-%02d-%sT23:59:00.000000000Z", year, month.getValue(), localDate.lengthOfMonth());
    }

    public String convertToFirstDayOfTheMonthAsString(Month month, int year) {
        return String.format("%s-%02d-01T00:00:00.000000000Z", year, month.getValue());
    }

    /**
     * Returns <strong>the very same instant</strong> the argument already denotes. Re-expressing an
     * {@link OffsetDateTime} in another zone with {@code atZoneSameInstant} changes only the offset
     * the value is <em>displayed</em> with, never the point on the time line, and {@link Instant}
     * carries no offset at all -- so the configured zone cannot influence the result. This is a
     * no-op, kept only because call sites still route through it; it does <strong>not</strong> snap
     * the value to the start of the local day, nor to any other boundary.
     *
     * <p>Aligning a query to local calendar days is done in InfluxQL with a {@code tz()} clause on
     * the {@code GROUP BY time(...)}, not by adjusting the range bounds -- see
     * {@code GetDatadisConsumptionRepositoryInflux} and {@code GetProductionRepositoryInflux}.
     */
    public Instant toLocalDayInstant(OffsetDateTime dateTime) {
        return dateTime
                .atZoneSameInstant(timeConfiguration.getZoneId())
                .toInstant();
    }

    public String convertFromInstantToStringDate(@NotNull Instant instant) {

        ZonedDateTime zonedDateTime = instant.atZone(timeConfiguration.getZoneId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);

        return formatter.format(zonedDateTime);
    }

    public String convertFromInstantToStringTime(@NotNull Instant instant) {

        ZonedDateTime zonedDateTime = instant.atZone(timeConfiguration.getZoneId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIME_FORMAT);

        return formatter.format(zonedDateTime);
    }
}
