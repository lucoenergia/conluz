package org.lucoenergia.conluz.infrastructure.shared.db;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code spring.jpa.open-in-view} is disabled (#480): no persistence context outlives a service call, and Spring Boot
 * does not register the interceptor that would keep one open for the whole request. That interceptor's configuration
 * is also what logs the startup warning about the setting, so its absence means the warning is gone.
 */
class OpenInViewDisabledTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void noPersistenceContextIsKeptOpenForTheWholeRequest() {
        assertThat(applicationContext.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }
}
