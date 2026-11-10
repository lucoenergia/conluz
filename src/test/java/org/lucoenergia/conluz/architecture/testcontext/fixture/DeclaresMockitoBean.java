package org.lucoenergia.conluz.architecture.testcontext.fixture;

import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Fixture for {@code TestContextConfigurationArchTest}: replaces a bean in a test class of its own.
 */
public abstract class DeclaresMockitoBean extends BaseIntegrationTest {

    @MockitoBean
    private DatadisSyncService replaced;
}
