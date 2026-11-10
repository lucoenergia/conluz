package org.lucoenergia.conluz.architecture.testcontext.fixture;

import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Fixture for {@code TestContextConfigurationArchTest}: starts a real server in a test class of its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class DeclaresRandomPort extends BaseIntegrationTest {
}
