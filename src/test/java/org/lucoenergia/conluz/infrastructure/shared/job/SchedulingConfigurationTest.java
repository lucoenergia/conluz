package org.lucoenergia.conluz.infrastructure.shared.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test
    void schedulesJobsWhenThePropertyIsNotSet() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void schedulesJobsWhenThePropertyIsTrue() {
        contextRunner.withPropertyValues("conluz.scheduling.enabled=true").run(context ->
                assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void doesNotScheduleJobsWhenThePropertyIsFalse() {
        contextRunner.withPropertyValues("conluz.scheduling.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
