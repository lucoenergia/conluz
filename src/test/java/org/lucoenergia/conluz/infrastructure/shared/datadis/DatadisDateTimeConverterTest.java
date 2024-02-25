package org.lucoenergia.conluz.infrastructure.shared.datadis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatadisDateTimeConverterTest {

    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);
    private final DatadisDateTimeConverter datadisDateTimeConverter = new DatadisDateTimeConverter(timeConfiguration);

    @BeforeEach
    void setup() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));
    }

    @Test
    void testConvertToDateWithValidInstant() {
        // Arrange
        Instant currentTimestamp = Instant.parse("2007-12-03T10:15:30.00Z");

        // Act
        String result = datadisDateTimeConverter.convertFromInstantToDate(currentTimestamp);

        // Assert
        assertEquals("2007/12", result);
    }

    @Test
    void testConvertToTimeWithValidInstant() {
        // Assemble
        Instant currentTimestamp = Instant.parse("2007-12-03T10:15:30.00Z");

        // Act
        String result = datadisDateTimeConverter.convertFromInstantToTime(currentTimestamp);

        // Assert
        assertEquals("11:15", result);
    }
}