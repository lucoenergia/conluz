package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.util.List;
import java.util.UUID;

@Transactional
@Disabled("These tests should not be included in a CI pipeline because connects with datadis.es, so, needs real data.")
class GetDatadisConsumptionRepositoryRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetDatadisConsumptionRepositoryRest datadisConsumptionRepositoryRest;
    @Autowired
    private DatadisConfigRepository datadisConfigRepository;

    @Test
    void testGetMonthlyConsumption() {
        final String authorizedNif = "nif";
        final String cups = "cups";
        final String distributorCode = "2";
        final Integer pointType = 5;
        final boolean isIsThirdParty = true;

        final User user = new User.Builder().personalId(authorizedNif).build();
        final Supply supply = new Supply.Builder()
                        .withCode(cups)
                        .withUser(user)
                        .withDistributorCode(distributorCode)
                        .withPointType(pointType)
                        .withThirdParty(isIsThirdParty)
                        .build();
        final Month month = Month.OCTOBER;
        final int year = 2023;

        // Set username and password you want to user for testing here
        final String username = "username";
        final String password = "password";

        // Save Datadis config on the DB
        final DatadisConfigEntity config = new DatadisConfigEntity();
        config.setId(UUID.randomUUID());
        config.setUsername(username);
        config.setPassword(password);
        datadisConfigRepository.save(config);

        List<DatadisConsumption> result = datadisConsumptionRepositoryRest.getHourlyConsumptionsByMonth(supply, month, year);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
    }
}
