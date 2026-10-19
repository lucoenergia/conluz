package org.lucoenergia.conluz.architecture.systemclock.fixture;

import java.time.*;

/**
 * Reads the JVM clock through {@code now(ZoneId)} of every {@code java.time} type that has one: all of them but
 * {@code Instant}.
 */
public class ReadsJavaTimeNowInAZone {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    void readNowInAZone() {
        LocalDate.now(ZONE);
        LocalDateTime.now(ZONE);
        LocalTime.now(ZONE);
        OffsetDateTime.now(ZONE);
        OffsetTime.now(ZONE);
        ZonedDateTime.now(ZONE);
        YearMonth.now(ZONE);
        Year.now(ZONE);
        MonthDay.now(ZONE);
    }
}
