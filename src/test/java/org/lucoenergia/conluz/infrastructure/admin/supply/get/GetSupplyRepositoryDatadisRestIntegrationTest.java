package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("These tests should not be included in a CI pipeline because connects with datadis.es, so, needs real data.")
@Transactional
class GetSupplyRepositoryDatadisRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyRepositoryDatadisRest getSupplyRepositoryDatadisRest;
    @Autowired
    private DatadisConfigRepository datadisConfigRepository;

    @Test
    void testGetSuppliesByUser() {

        // Set username and password you want to user for testing here
        final String username = "username";
        final String password = "password";

        // Save Datadis config on the DB
        final DatadisConfigEntity config = new DatadisConfigEntity();
        config.setId(UUID.randomUUID());
        config.setUsername(username);
        config.setPassword(password);
        datadisConfigRepository.save(config);

        User user = new User.Builder()
                .id(UUID.randomUUID())
                .personalId("personalId")
                .number(1)
                .fullName("John Doe")
                .email("username@email.com")
                .enabled(true)
                .build();

        List<DatadisSupply> suppliesByUser = getSupplyRepositoryDatadisRest.getSuppliesByUser(user);

        assertEquals(1, suppliesByUser.size());
    }
}