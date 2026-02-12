package org.lucoenergia.conluz.infrastructure.shared.time;

import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Component
public class DateConverter {

    public static final String DATE_FORMAT = "yyyy/MM/dd";
    public static final String TIME_FORMAT = "HH:mm";

    private final TimeConfiguration timeConfiguration;

    public DateConverter(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    public int getYearFromStringDate(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM");
        return YearMonth.parse(dateString, formatter).getYear();
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
