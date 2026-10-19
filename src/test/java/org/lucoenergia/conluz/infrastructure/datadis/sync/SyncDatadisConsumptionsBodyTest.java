package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncDatadisConsumptionsBodyTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    /** Today as the body reads it: in the JVM's default zone. */
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneId.systemDefault());

    @Test
    void testGetEndDate_WhenYearIsInThePast_ShouldReturnLastDayOfYear() {
        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2020);

        assertEquals(LocalDate.of(2020, 12, 31), body.getEndDate(CLOCK),
                "The end date should be the last day of the year for past years.");
    }

    @Test
    void testGetEndDate_WhenYearIsInTheFuture_ShouldReturnToday() {
        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(TODAY.getYear() + 1);

        assertEquals(TODAY, body.getEndDate(CLOCK), "The end date should be today for future years.");
    }

    @Test
    void testGetEndDate_WhenYearIsTheCurrentYear_ShouldReturnToday() {
        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(TODAY.getYear());

        assertEquals(TODAY, body.getEndDate(CLOCK), "The end date should be today for the current year.");
    }
}
