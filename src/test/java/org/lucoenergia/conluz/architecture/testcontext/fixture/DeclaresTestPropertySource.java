package org.lucoenergia.conluz.architecture.testcontext.fixture;

import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Fixture for {@code TestContextConfigurationArchTest}: sets a property in a test class of its own.
 */
@TestPropertySource(properties = "conluz.mail.enabled=true")
public abstract class DeclaresTestPropertySource extends BaseIntegrationTest {
}
