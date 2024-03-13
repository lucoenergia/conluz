package org.lucoenergia.conluz.infrastructure.shared.time;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringToLocalDateConverterTest {
    
    @Test
    void testConvertValidDateString() {
        String dateString = "2023/11/25";
        LocalDate expected = LocalDate.of(2023, 11, 25);
        LocalDate actual = StringToLocalDateConverter.convert(dateString);
        assertEquals(expected, actual);
    }
    
    @Test
    void testConvertInvalidDateString() {
        String invalidDateString = "11/2023/25";
        assertThrows(DateTimeParseException.class, () -> StringToLocalDateConverter.convert(invalidDateString));
    }
}