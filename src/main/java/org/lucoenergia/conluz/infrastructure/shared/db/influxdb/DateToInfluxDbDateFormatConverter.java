package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Component
public class DateToInfluxDbDateFormatConverter {

    private final TimeConfiguration timeConfiguration;

    public DateToInfluxDbDateFormatConverter(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    public String convert(OffsetDateTime time) {

        // Convert to UTC by extracting the Instant
        Instant instant = time.toInstant();

        // Define a DateTimeFormatter for InfluxDB timestamp format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'");

        // Format the Instant to the desired InfluxDB timestamp format
        return instant.atZone(ZoneOffset.UTC).format(formatter);
    }

    public String convertToLastDayOfTheMonth(Month month, int year) {
        LocalDate localDate = LocalDate.of(year, month, 1);

        return String.format("%s-%02d-%sT23:59:00.000000000Z", year, month.getValue(), localDate.lengthOfMonth());
    }

    public String convertToFirstDayOfTheMonth(Month month, int year) {
        return String.format("%s-%02d-01T00:00:00.000000000Z", year, month.getValue());
    }
}
