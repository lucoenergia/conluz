package org.lucoenergia.conluz.infrastructure.shared.time;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;

/**
 * Replaces the application {@link Clock} with a {@link MutableClock}, so that tests can move time instead of
 * waiting for it.
 */
@TestConfiguration
public class MutableClockConfiguration {

    /**
     * Where the clock is at the start of every test.
     */
    public static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(START);
    }
}
