package org.lucoenergia.conluz.shared.db.influxdb;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class OffsetDateTimeToInfluxDbDateFormatConverter {

    public static String convert(OffsetDateTime time) {

        // Convert to UTC by extracting the Instant
        Instant instant = time.toInstant();

        // Define a DateTimeFormatter for InfluxDB timestamp format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'");

        // Format the Instant to the desired InfluxDB timestamp format
        return instant.atZone(ZoneOffset.UTC).format(formatter);
    }
}
