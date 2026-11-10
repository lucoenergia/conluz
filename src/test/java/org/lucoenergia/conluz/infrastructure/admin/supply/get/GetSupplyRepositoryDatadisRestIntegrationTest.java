package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("These tests should not be included in a CI pipeline because connects with datadis, so, needs real data.")
@Transactional
class GetSupplyRepositoryDatadisRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyRepositoryDatadisRest getSupplyRepositoryDatadisRest;

    @Test
    void testGetSuppliesByUser() {

        // Set username and password you want to user for testing here
        final String username = "username";
        final String password = "password";

        final DatadisConfig config = new DatadisConfig.Builder()
                .setUsername(username)
                .setPassword(password)
                .setBaseUrl("https://datadis.es")
                .build();

        User user = new User.Builder()
                .id(UUID.randomUUID())
                .personalId("personalId")
                .number(1)
                .fullName("John Doe")
                .email("username@email.com")
                .enabled(true)
                .build();

        List<DatadisSupply> suppliesByUser = getSupplyRepositoryDatadisRest.getSuppliesByUser(config,
                "Bearer " + datadisAuthorizer.getAuthToken(config), user);

        assertEquals(1, suppliesByUser.size());
    }
}