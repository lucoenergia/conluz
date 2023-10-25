package org.lucoenergia.conluz.shared.db.influxdb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;

@SpringBootTest
public class OffsetDateTimeToInfluxDbDateFormatConverterTest {

    @Test
    void testConvert() {

        String dateTimeString = "2023-10-26T12:25:33Z";
        OffsetDateTime dateTime = OffsetDateTime.parse(dateTimeString);

        String result = OffsetDateTimeToInfluxDbDateFormatConverter.convert(dateTime);

        Assertions.assertEquals("2023-10-26T12:25:33.000000000Z", result);
    }
}
