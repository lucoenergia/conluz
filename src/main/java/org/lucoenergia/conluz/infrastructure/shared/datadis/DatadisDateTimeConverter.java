package org.lucoenergia.conluz.infrastructure.shared.datadis;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DatadisDateTimeConverter {

    public static final String DATE_FORMAT = "yyyy/MM";
    public static final String TIME_FORMAT = "HH:mm";

    private final TimeConfiguration timeConfiguration;

    public DatadisDateTimeConverter(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    public String convertFromInstantToDate(@NotNull Instant instant) {

        ZonedDateTime zonedDateTime = instant.atZone(timeConfiguration.getZoneId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);

        return formatter.format(zonedDateTime);
    }

    public String convertFromInstantToTime(@NotNull Instant instant) {

        ZonedDateTime zonedDateTime = instant.atZone(timeConfiguration.getZoneId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIME_FORMAT);

        return formatter.format(zonedDateTime);
    }
}
