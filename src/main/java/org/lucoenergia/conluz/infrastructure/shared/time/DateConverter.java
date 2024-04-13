package org.lucoenergia.conluz.infrastructure.shared.time;

import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Component
public class DateConverter {

    private final TimeConfiguration timeConfiguration;

    public DateConverter(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    public long convertStringDateToMilliseconds(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm");

        ZoneId zoneId = timeConfiguration.getZoneId();

        LocalDateTime dateTime = LocalDateTime.parse(dateString, formatter);
        return dateTime.atZone(zoneId).toInstant().toEpochMilli();
    }

    public long convertOffsetDateTimeToMilliseconds(OffsetDateTime time) {
        return time.toInstant().toEpochMilli();
    }

    public OffsetDateTime convertInstantToOffsetDateTime(Instant instant) {

        ZoneId zoneId = timeConfiguration.getZoneId();

        ZonedDateTime zonedDateTime = instant.atZone(zoneId);

        return instant.atOffset(zonedDateTime.getOffset());
    }

    public LocalDate convertStringToLocalDate(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        return LocalDate.parse(dateString, formatter);
    }

    public OffsetDateTime convertMillisecondsToOffsetDateTime(long milliseconds) {
        return Instant.ofEpochMilli(milliseconds).atZone(timeConfiguration.getZoneId()).toOffsetDateTime();
    }

    public OffsetDateTime now() {
        return timeConfiguration.now();
    }
}
