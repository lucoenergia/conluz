package org.lucoenergia.conluz.infrastructure.shared.time;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class DateToMillisecondsConverter {

    private final TimeConfiguration timeConfiguration;

    public DateToMillisecondsConverter(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    public long convert(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm");

        ZoneId zoneId = timeConfiguration.getZoneId();

        LocalDateTime dateTime = LocalDateTime.parse(dateString, formatter);
        return dateTime.atZone(zoneId).toInstant().toEpochMilli();
    }
}
