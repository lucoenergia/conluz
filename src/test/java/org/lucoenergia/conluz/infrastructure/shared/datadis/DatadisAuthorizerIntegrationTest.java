package org.lucoenergia.conluz.infrastructure.shared.datadis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;


@Transactional
@Disabled("These tests should not be included in a CI pipeline because connects with datadis, so, needs real data.")
class DatadisAuthorizerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DatadisAuthorizer datadisAuthorizer;

    @Test
    void testGetAuthToken() {

        // Set username and password you want to user for testing here
        final String username = "";
        final String password = "";

        final DatadisConfig config = new DatadisConfig.Builder()
                .setUsername(username)
                .setPassword(password)
                .setBaseUrl("https://datadis.es")
                .build();

        // Get auth token
        final String authToken = datadisAuthorizer.getAuthToken(config);

        Assertions.assertNotNull(authToken);
    }
}
