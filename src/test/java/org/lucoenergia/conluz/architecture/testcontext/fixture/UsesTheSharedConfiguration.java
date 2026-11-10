package org.lucoenergia.conluz.architecture.testcontext.fixture;

import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Fixture for {@code TestContextConfigurationArchTest}: repeats the base class's bare {@code @SpringBootTest}, which
 * changes nothing.
 */
@SpringBootTest
public abstract class UsesTheSharedConfiguration extends BaseIntegrationTest {
}
