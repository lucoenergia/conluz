package org.lucoenergia.conluz.infrastructure.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfiguration {

    /**
     * The application's single source of "now". Every read of the current time goes through this bean, so that
     * tests can replace it and move time instead of waiting for it.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
