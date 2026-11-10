package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import java.time.Month;
import java.util.List;

@Transactional
@Disabled("These tests should not be included in a CI pipeline because connects with datadis, so, needs real data.")
class GetDatadisConsumptionRepositoryRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetDatadisConsumptionRepositoryRest datadisConsumptionRepositoryRest;

    @Test
    void testGetMonthlyConsumption() {
        final String authorizedNif = "nif";
        final String cups = "cups";
        final String distributorCode = "2";
        final Integer pointType = 5;

        final User user = new User.Builder().personalId(authorizedNif).build();
        final Supply supply = new Supply.Builder()
                        .withCode(cups)
                        .withUser(user)
                        .withDistributor(new SupplyDistributor.Builder()
                                .withCode(distributorCode)
                                .withPointType(pointType)
                                .build())
                        .build();
        final Month month = Month.OCTOBER;
        final int year = 2023;

        // Set username and password you want to user for testing here.
        // The owner NIF differs from this username, so the request includes the authorizedNif.
        final String username = "username";
        final String password = "password";

        final DatadisConfig config = new DatadisConfig.Builder()
                .setUsername(username)
                .setPassword(password)
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .build();
        final String bearerToken = "Bearer " + datadisAuthorizer.getAuthToken(config);

        List<DatadisConsumption> result = datadisConsumptionRepositoryRest.getHourlyConsumptionsByMonth(config,
                bearerToken, supply, month, year);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
    }
}
