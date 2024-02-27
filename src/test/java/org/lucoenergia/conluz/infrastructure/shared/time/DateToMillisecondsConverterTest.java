package org.lucoenergia.conluz.infrastructure.shared.time;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.*;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class DateToMillisecondsConverterTest {

    private TimeConfiguration timeConfiguration;

    @BeforeEach
    void setUp() {
        timeConfiguration = Mockito.mock(TimeConfiguration.class);
    }

    @Test
    void testConvert() {
        // Given
        String dateString = "2023/03/23T14:30";
        String zone = "UTC";
        long expectedMillis = 1679581800000L;  // expected milliseconds representation for the above date in Europe/Madrid time zone.

        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of(zone));

        DateToMillisecondsConverter converter = new DateToMillisecondsConverter(timeConfiguration);

        // When 
        long actualMillis = converter.convert(dateString);

        // Then
        assertEquals(expectedMillis, actualMillis);
    }

    @Test
    void testConvertWithDifferentTimezone() {
        // Given
        String dateString = "2023/03/23T14:30";
        String zone = "Europe/Madrid";            // IST, India Standard Time, is UTC+5:30.
        long expectedMillis = 1679578200000L;  // expected milliseconds representation for the above date in IST time zone.

        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of(zone));

        DateToMillisecondsConverter converter = new DateToMillisecondsConverter(timeConfiguration);

        // When
        long actualMillis = converter.convert(dateString);

        // Then
        assertEquals(expectedMillis, actualMillis);
    }

    @Test
    void testConvertWithNonStandardDateFormat() {
        // Given
        String dateString = "2023-03-23T14:30";  // non-standard date format.
        String zone = "UTC";

        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of(zone));

        DateToMillisecondsConverter converter = new DateToMillisecondsConverter(timeConfiguration);

        // When-Then
        Assertions.assertThrows(DateTimeParseException.class, () -> converter.convert(dateString));
    }
}