package org.lucoenergia.conluz.infrastructure.shared.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MutableClockTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final MutableClock clock = new MutableClock(START);

    @Test
    void itOnlyMovesWhenATestMovesIt_forwardsOrBackwards() {
        assertEquals(START, clock.instant());

        clock.advance(Duration.ofHours(1));
        assertEquals(START.plus(Duration.ofHours(1)), clock.instant());

        clock.setInstant(START.minus(Duration.ofDays(30)));
        assertEquals(START.minus(Duration.ofDays(30)), clock.instant());
    }

    @Test
    void aClockInAnotherZone_readsTheSameInstant_andFollowsEveryLaterMove() {
        Clock madrid = clock.withZone(MADRID);
        assertEquals(MADRID, madrid.getZone());
        assertEquals(ZoneOffset.UTC, clock.getZone());

        clock.setInstant(Instant.parse("2026-03-31T22:30:00Z"));
        assertEquals(Instant.parse("2026-03-31T22:30:00Z"), madrid.instant());
        assertEquals(LocalDate.parse("2026-04-01"), LocalDate.now(madrid));
        assertEquals(LocalDate.parse("2026-03-31"), LocalDate.now(clock));

        ((MutableClock) madrid).advance(Duration.ofDays(1));
        assertEquals(Instant.parse("2026-04-01T22:30:00Z"), clock.instant());
    }

    @Test
    void theSameZoneReturnsTheSameClock() {
        assertSame(clock, clock.withZone(ZoneOffset.UTC));
    }
}
