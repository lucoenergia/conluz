package org.lucoenergia.conluz.architecture.systemclock.fixture;

import java.time.*;

/**
 * Reads the JVM clock through the no-argument {@code now()} of every {@code java.time} type.
 */
public class ReadsJavaTimeNow {

    void readNow() {
        Instant.now();
        LocalDate.now();
        LocalDateTime.now();
        LocalTime.now();
        OffsetDateTime.now();
        OffsetTime.now();
        ZonedDateTime.now();
        YearMonth.now();
        Year.now();
        MonthDay.now();
    }
}
