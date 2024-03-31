package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Month;
import java.time.OffsetDateTime;

class DateToInfluxDbDateFormatConverterTest {

    private final DateToInfluxDbDateFormatConverter converter = new DateToInfluxDbDateFormatConverter();

    @Test
    void testConvert() {

        String dateTimeString = "2023-10-26T12:25:33Z";
        OffsetDateTime dateTime = OffsetDateTime.parse(dateTimeString);

        String result = converter.convert(dateTime);

        Assertions.assertEquals("2023-10-26T12:25:33.000000000Z", result);
    }

    @Test
    void testConvertToLastDayOfTheMonth() {

        String result = converter.convertToLastDayOfTheMonth(Month.MARCH, 2023);

        // For the date "2023-03-31T23:59:00", the expected result format is "2023-03-31T23:59:00.000000000Z"
        String expected = "2023-03-31T23:59:00.000000000Z";

        // Assert the result
        Assertions.assertEquals(expected, result);
    }

    @Test
    void convertToLastDayOfTheMonthFebruary() {
        String result = converter.convertToLastDayOfTheMonth(Month.FEBRUARY, 2024);

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
        String actual = converter.convertToFirstDayOfTheMonth(month, year);

        // then
        Assertions.assertEquals("2023-01-01T00:00:00.000000000Z", actual);
    }

    @Test
    void shouldHandleLeapYear() {
        // given
        Month month = Month.of(2);
        int year = 2024;

        // when
        String actual = converter.convertToFirstDayOfTheMonth(month, year);

        // then
        Assertions.assertEquals("2024-02-01T00:00:00.000000000Z", actual);
    }
}
