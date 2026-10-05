package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthenticationThrottleConfig {

    /**
     * The clock the throttling windows are measured with, injected so that tests can move it.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
