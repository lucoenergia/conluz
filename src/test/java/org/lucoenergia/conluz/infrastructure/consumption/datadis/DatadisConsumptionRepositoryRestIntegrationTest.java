package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.consumption.datadis.Consumption;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisConfig;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Transactional
@Disabled("These tests should not be included in a CI pipeline because connects with datadis.es, so, needs real data.")
class DatadisConsumptionRepositoryRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DatadisConsumptionRepositoryRest datadisConsumptionRepositoryRest;
    @Autowired
    private DatadisConfigRepository datadisConfigRepository;

    @Test
    void testGetMonthlyConsumption() {
        final String authorizedNif = "nif";
        final String cups = "cups";
        final String distributorCode = "2";
        final String pointType = "5";

        final User user = new User.Builder().personalId(authorizedNif).build();
        final List<Supply> supplies = Arrays.asList(
                new Supply.Builder()
                        .withId(cups)
                        .withUser(user)
                        .withDistributorCode(distributorCode)
                        .withPointType(pointType)
                        .build()
        );
        final Month month = Month.OCTOBER;
        final int year = 2023;

        // Set username and password you want to user for testing here
        final String username = "username";
        final String password = "password";

        // Save Datadis config on the DB
        final DatadisConfig config = new DatadisConfig();
        config.setId(UUID.randomUUID());
        config.setUsername(username);
        config.setPassword(password);
        datadisConfigRepository.save(config);

        Map<String, List<Consumption>> result = datadisConsumptionRepositoryRest.getMonthlyConsumption(supplies, month, year);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.containsKey("ES0031300197172001CW0F"));
        Assertions.assertFalse(result.get("ES0031300197172001CW0F").isEmpty());
    }
}