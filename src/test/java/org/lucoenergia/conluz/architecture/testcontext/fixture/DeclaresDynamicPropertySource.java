package org.lucoenergia.conluz.architecture.testcontext.fixture;

import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Fixture for {@code TestContextConfigurationArchTest}: sets a property in a test class of its own.
 */
public abstract class DeclaresDynamicPropertySource extends BaseIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("conluz.mail.enabled", () -> "true");
    }
}
