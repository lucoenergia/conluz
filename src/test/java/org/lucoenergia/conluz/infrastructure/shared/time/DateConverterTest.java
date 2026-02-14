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
    void testGetYearFromStringDate() {
        String dateString = "2023/11";
        int expected = 2023;
        int actual = converter.getYearFromStringDate(dateString);
        assertEquals(expected, actual);
    }

    @Test
    void testGetYearFromStringDateWithFullDate() {
        // Test with full date format (yyyy/MM/dd) - the actual format from hourly consumptions
        String dateString = "2023/11/15";
        int expected = 2023;
        int actual = converter.getYearFromStringDate(dateString);
        assertEquals(expected, actual);
    }

    @Test
    void testGetYearFromStringDateWithFirstDayOfMonth() {
        // Test edge case with first day of month - the exact error case from production
        String dateString = "2025/01/01";
        int expected = 2025;
        int actual = converter.getYearFromStringDate(dateString);
        assertEquals(expected, actual);
    }

    @Test
    void testGetYearFromStringDateWithLastDayOfMonth() {
        // Test edge case with last day of month
        String dateString = "2024/12/31";
        int expected = 2024;
        int actual = converter.getYearFromStringDate(dateString);
        assertEquals(expected, actual);
    }

    @Test
    void testGetYearFromStringDateWithLeapYearDate() {
        // Test leap year date
        String dateString = "2024/02/29";
        int expected = 2024;
        int actual = converter.getYearFromStringDate(dateString);
        assertEquals(expected, actual);
    }

    @Test
    void testGetYearFromStringDateBackwardCompatibility() {
        // Ensure existing format (yyyy/MM) still works - validates backward compatibility
        String dateString = "2023/11";
        int expected = 2023;
        int actual = converter.getYearFromStringDate(dateString);
        assertEquals(expected, actual);
    }

    @Test
    void testGetYearFromStringDateWithInvalidFormat() {
        // Test with invalid format (dashes instead of slashes)
        String invalidDateString = "2023-11-15";
        assertThrows(IllegalArgumentException.class, () -> converter.getYearFromStringDate(invalidDateString));
    }

    @Test
    void testGetYearFromStringDateWithInvalidDate() {
        // Test with invalid date (month > 12)
        String invalidDateString = "2023/13/01";
        assertThrows(IllegalArgumentException.class, () -> converter.getYearFromStringDate(invalidDateString));
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

    @Test
    void testConvertMillisecondsToOffsetDateTime() {
        // Given
        long millis = 1679581800000L; // represents "2023-03-23T14:30:00Z"
        ZoneOffset offset = ZoneOffset.ofHours(0);

        // When
        OffsetDateTime result = converter.convertMillisecondsToOffsetDateTime(millis);

        // Then
        Assertions.assertEquals(2023, result.getYear());
        Assertions.assertEquals(Month.MARCH, result.getMonth());
        Assertions.assertEquals(23, result.getDayOfMonth());
        Assertions.assertEquals(14, result.getHour());
        Assertions.assertEquals(30, result.getMinute());
        Assertions.assertEquals(0, result.getSecond());
        Assertions.assertEquals(offset, result.getOffset());
    }

    @Test
    void testConvertFromInstantToStringDateWithUTC() {
        // Given
        Instant instant = Instant.parse("2023-04-15T14:30:45Z");
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("UTC"));

        // When
        String result = converter.convertFromInstantToStringDate(instant);

        // Then
        assertEquals("2023/04/15", result);
    }

    @Test
    void testConvertFromInstantToStringDateWithEuropeMadrid() {
        // Given - UTC time 14:30:45
        Instant instant = Instant.parse("2023-04-15T14:30:45Z");
        // Europe/Madrid is UTC+2 in summer (CEST)
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        // When
        String result = converter.convertFromInstantToStringDate(instant);

        // Then - Should still be same date (2023/04/15) as 14:30 UTC -> 16:30 Madrid
        assertEquals("2023/04/15", result);
    }

    @Test
    void testConvertFromInstantToStringDateCrossingMidnight() {
        // Given - UTC time 23:30:00
        Instant instant = Instant.parse("2023-04-15T23:30:00Z");
        // Asia/Tokyo is UTC+9
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Asia/Tokyo"));

        // When
        String result = converter.convertFromInstantToStringDate(instant);

        // Then - Should be next day (2023/04/16) as 23:30 UTC + 9 hours -> 08:30 next day
        assertEquals("2023/04/16", result);
    }

    @Test
    void testConvertFromInstantToStringTimeWithUTC() {
        // Given
        Instant instant = Instant.parse("2023-04-15T14:30:45Z");
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("UTC"));

        // When
        String result = converter.convertFromInstantToStringTime(instant);

        // Then
        assertEquals("14:30", result);
    }

    @Test
    void testConvertFromInstantToStringTimeWithEuropeMadrid() {
        // Given - UTC time 14:30:45
        Instant instant = Instant.parse("2023-04-15T14:30:45Z");
        // Europe/Madrid is UTC+2 in summer (CEST) during April
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        // When
        String result = converter.convertFromInstantToStringTime(instant);

        // Then - Should be 16:30 (14:30 UTC + 2 hours)
        assertEquals("16:30", result);
    }

    @Test
    void testConvertFromInstantToStringTimeWithNegativeOffset() {
        // Given - UTC time 10:45:30
        Instant instant = Instant.parse("2023-04-15T10:45:30Z");
        // America/New_York is UTC-4 in summer (EDT) during April
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("America/New_York"));

        // When
        String result = converter.convertFromInstantToStringTime(instant);

        // Then - Should be 06:45 (10:45 UTC - 4 hours)
        assertEquals("06:45", result);
    }

    @Test
    void testConvertFromInstantToStringTimeMidnight() {
        // Given - UTC midnight
        Instant instant = Instant.parse("2023-04-15T00:00:00Z");
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("UTC"));

        // When
        String result = converter.convertFromInstantToStringTime(instant);

        // Then
        assertEquals("00:00", result);
    }

    @Test
    void testConvertFromInstantToStringTimeEndOfDay() {
        // Given - UTC time 23:59:59
        Instant instant = Instant.parse("2023-04-15T23:59:59Z");
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("UTC"));

        // When
        String result = converter.convertFromInstantToStringTime(instant);

        // Then
        assertEquals("23:59", result);
    }
}