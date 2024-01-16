package org.lucoenergia.conluz.infrastructure.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.EnergyProductionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.MockInfluxDbConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetInstantProductionControllerTest extends BaseControllerTest {

    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private EnergyProductionInfluxLoader energyProductionInfluxLoader;

    @BeforeEach
    void beforeEach() {
        energyProductionInfluxLoader.loadData(MockInfluxDbConfiguration.INFLUX_DB_NAME);
    }

    @BeforeEach
    void afterEach() {
        energyProductionInfluxLoader.clearData();
    }

    @Test
    void testGetInstantProduction() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    void testGetInstantProductionBySupply() throws Exception {

        // Create some supplies
        supplyRepository.saveAll(Arrays.asList(
                new SupplyEntity("1", "My house", "Fake street", 0.030763f, true),
                new SupplyEntity("2", "The garage", "Sesame Street 666", 0.015380f, true),
                new SupplyEntity("3", "My daughter's house", "Real street 22", 0.041017f, true)
        ));

        String authHeader = loginAsDefaultAdmin();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supplyId", supplyId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    void testGetInstantProductionByUnknownSupply() throws Exception {

        String authHeader = loginAsDefaultAdmin();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supplyId", supplyId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString("\"message\":\"El punto de suministro con identificador '1' no ha sido encontrado. Revise que el identificador sea correcto.\"")));
    }

    @Test
    void testGetInstantProductionWithWrongParameter() throws Exception {

        String authHeader = loginAsDefaultAdmin();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supply", supplyId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }
}
