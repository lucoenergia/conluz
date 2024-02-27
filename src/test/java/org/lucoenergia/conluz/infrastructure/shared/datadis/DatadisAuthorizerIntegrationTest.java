package org.lucoenergia.conluz.infrastructure.shared.datadis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
@Disabled("These tests should not be included in a CI pipeline because connects with datadis.es, so, needs real data.")
class DatadisAuthorizerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DatadisAuthorizer datadisAuthorizer;
    @Autowired
    private DatadisConfigRepository datadisConfigRepository;

    @Test
    void testGetAuthToken() {

        // Set username and password you want to user for testing here
        final String username = "";
        final String password = "";

        // Save Datadis config on the DB
        final DatadisConfig config = new DatadisConfig();
        config.setId(UUID.randomUUID());
        config.setUsername(username);
        config.setPassword(password);
        datadisConfigRepository.save(config);

        // Get auth token
        final String authToken = datadisAuthorizer.getAuthToken();

        Assertions.assertNotNull(authToken);
    }
}
