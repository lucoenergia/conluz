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

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
