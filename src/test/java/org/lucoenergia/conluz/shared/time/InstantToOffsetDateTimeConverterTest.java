package org.lucoenergia.conluz.shared.time;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.*;

@SpringBootTest
public class InstantToOffsetDateTimeConverterTest {

    @Autowired
    private InstantToOffsetDateTimeConverter converter;

    @Autowired
    private TimeConfiguration timeConfiguration;

    @Test
    void testConvert() {

        String dateTimeString = "2023-10-26T12:25:33Z";
        Instant instant = Instant.parse(dateTimeString);

        LocalDateTime localDateTime = LocalDateTime.parse(dateTimeString.substring(0, dateTimeString.length() - 1));
        ZonedDateTime sourceZonedDateTime = localDateTime.atZone(ZoneId.of("UTC"));

        // Convert ZonedDateTime from the source time zone to the target time zone
        ZonedDateTime targetZonedDateTime = sourceZonedDateTime.withZoneSameInstant(timeConfiguration.getZoneId());

        OffsetDateTime result = converter.convert(instant);

        Assertions.assertEquals(2023, result.getYear());
        Assertions.assertEquals(Month.OCTOBER, result.getMonth());
        Assertions.assertEquals(26, result.getDayOfMonth());
        Assertions.assertEquals(targetZonedDateTime.getHour(), result.getHour());
        Assertions.assertEquals(25, result.getMinute());
        Assertions.assertEquals(33, result.getSecond());
        Assertions.assertEquals(timeConfiguration.getOffset(), result.getOffset());
    }
}
