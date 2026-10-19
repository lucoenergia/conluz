package org.lucoenergia.conluz.architecture.systemclock.fixture;

import java.time.*;
import java.util.Date;

/**
 * Reads the time only in the ways the rule allows: from a given {@link Clock}, from a given number of milliseconds,
 * and as a duration with {@link System#nanoTime()}.
 */
public class AllowedTimeReads {

    void readFrom(Clock clock) {
        Instant.now(clock);
        LocalDate.now(clock);
        LocalDateTime.now(clock);
        LocalTime.now(clock);
        OffsetDateTime.now(clock);
        OffsetTime.now(clock);
        ZonedDateTime.now(clock);
        YearMonth.now(clock);
        Year.now(clock);
        MonthDay.now(clock);
        LocalDate.now(clock.withZone(ZoneId.of("Europe/Madrid")));
        new Date(clock.millis());
        System.nanoTime();
    }
}
