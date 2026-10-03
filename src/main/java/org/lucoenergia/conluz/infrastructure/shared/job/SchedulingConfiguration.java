package org.lucoenergia.conluz.infrastructure.shared.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs the {@code @Scheduled} jobs. Enabled unless {@code conluz.scheduling.enabled} is set to
 * {@code false}, so a deployment that does not set it keeps running them.
 *
 * <p>The test configuration turns it off: a job firing on the scheduler's thread in the middle of a
 * test races with what the test measures or asserts (for example, statement counts read from the
 * application-wide Hibernate statistics). Jobs are tested by calling them directly.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "conluz.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
