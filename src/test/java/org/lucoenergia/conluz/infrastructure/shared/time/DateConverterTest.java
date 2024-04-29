package org.lucoenergia.conluz.infrastructure.shared.time;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.*;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class DateConverterTest {

    private DateConverter converter;
    private TimeConfiguration timeConfiguration;

    @BeforeEach
    void setUp() {
        timeConfiguration = Mockito.mock(TimeConfiguration.class);
        converter = new DateConverter(timeConfiguration);
    }

    @Test
    void testConvertStringDateToMilliseconds() {
        // Given
        String dateString = "2023/03/23T14:30";
        String zone = "UTC";
        long expectedMillis = 1679581800000L;  // expected milliseconds representation for the above date in Europe/Madrid time zone.

        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of(zone));

        // When
        long actualMillis = converter.convertStringDateToMilliseconds(dateString);

        // Then
        assertEquals(expectedMillis, actualMillis);
    }

    @Test
    void testConvertStringDateToMillisecondsWithDifferentTimezone() {
        // Given
        String dateString = "2023/03/23T14:30";
        String zone = "Europe/Madrid";            // IST, India Standard Time, is UTC+5:30.
        long expectedMillis = 1679578200000L;  // expected milliseconds representation for the above date in IST time zone.

        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of(zone));

        // When
        long actualMillis = converter.convertStringDateToMilliseconds(dateString);

        // Then
        assertEquals(expectedMillis, actualMillis);
    }

    @Test
    void testConvertStringDateToMillisecondsWithNonStandardDateFormat() {
        // Given
        String dateString = "2023-03-23T14:30";  // non-standard date format.
        String zone = "UTC";

        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of(zone));

        // When-Then
        Assertions.assertThrows(DateTimeParseException.class, () -> converter.convertStringDateToMilliseconds(dateString));
    }

    @Test
    void testConvertInstantToOffsetDateTime() {

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

        OffsetDateTime result = converter.convertInstantToOffsetDateTime(instant);

        Assertions.assertEquals(2023, result.getYear());
        Assertions.assertEquals(Month.OCTOBER, result.getMonth());
        Assertions.assertEquals(26, result.getDayOfMonth());
        Assertions.assertEquals(targetZonedDateTime.getHour(), result.getHour());
        Assertions.assertEquals(25, result.getMinute());
        Assertions.assertEquals(33, result.getSecond());
        Assertions.assertEquals(timeConfiguration.getOffset(instant), result.getOffset());
    }

    @Test
    void testConvertStringToLocalDateValidDateString() {
        String dateString = "2023/11/25";
        LocalDate expected = LocalDate.of(2023, 11, 25);
        LocalDate actual = converter.convertStringToLocalDate(dateString);
        assertEquals(expected, actual);
    }

    @Test
    void testConvertStringToLocalDateInvalidDateString() {
        String invalidDateString = "11/2023/25";
        assertThrows(DateTimeParseException.class, () -> converter.convertStringToLocalDate(invalidDateString));
    }

    @Test
    void testConvertToString() {

        String dateTimeString = "2023-10-26T12:25:33Z";
        OffsetDateTime dateTime = OffsetDateTime.parse(dateTimeString);

        String result = converter.convertToString(dateTime);

        Assertions.assertEquals("2023-10-26T12:25:33.000000000Z", result);
    }

    @Test
    void testConvertToLastDayOfTheMonth() {

        String result = converter.convertToLastDayOfTheMonthAsString(Month.MARCH, 2023);

        // For the date "2023-03-31T23:59:00", the expected result format is "2023-03-31T23:59:00.000000000Z"
        String expected = "2023-03-31T23:59:00.000000000Z";

        // Assert the result
        Assertions.assertEquals(expected, result);
    }

    @Test
    void convertToLastDayOfTheMonthFebruary() {
        String result = converter.convertToLastDayOfTheMonthAsString(Month.FEBRUARY, 2024);

        // For the date "2024-02-29T23:59:00", the expected result format is "2024-02-29T23:59:00.000000000Z"
        // We consider 2024 as a leap year which includes 29 days for February
        String expected = "2024-02-29T23:59:00.000000000Z";

        // Assert the result
        Assertions.assertEquals(expected, result);
    }

    @Test
    void shouldConvertToFirstDayOfMonth() {
        // given
        Month month = Month.of(1);
        int year = 2023;

        // when
        String actual = converter.convertToFirstDayOfTheMonthAsString(month, year);

        // then
        Assertions.assertEquals("2023-01-01T00:00:00.000000000Z", actual);
    }

    @Test
    void shouldHandleLeapYear() {
        // given
        Month month = Month.of(2);
        int year = 2024;

        // when
        String actual = converter.convertToFirstDayOfTheMonthAsString(month, year);

        // then
        Assertions.assertEquals("2024-02-01T00:00:00.000000000Z", actual);
    }
}