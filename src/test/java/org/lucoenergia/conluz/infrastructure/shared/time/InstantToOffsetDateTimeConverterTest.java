package org.lucoenergia.conluz.infrastructure.shared.time;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InstantToOffsetDateTimeConverterTest {

    @InjectMocks
    private InstantToOffsetDateTimeConverter converter;
    @Mock
    private TimeConfiguration timeConfiguration;

    @Test
    void testConvert() {

        String dateTimeString = "2023-10-26T12:25:33Z";
        Instant instant = Instant.parse(dateTimeString);

        when(timeConfiguration.getZoneId())
                .thenReturn(ZoneId.of("Europe/Madrid"));
        when(timeConfiguration.getOffset(instant))
                .thenReturn(ZoneOffset.ofHours(2));

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
        Assertions.assertEquals(timeConfiguration.getOffset(instant), result.getOffset());
    }
}
