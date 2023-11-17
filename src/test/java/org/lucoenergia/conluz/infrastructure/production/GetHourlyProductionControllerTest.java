package org.lucoenergia.conluz.infrastructure.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.EnergyProductionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.MockInfluxDbConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.security.BasicAuthHeaderGenerator;
import org.lucoenergia.conluz.infrastructure.shared.security.MockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class GetHourlyProductionControllerTest extends BaseIntegrationTest {

    private static final String START_DATE = "2023-09-01T00:00:00.000+02:00";
    private static final String END_DATE = "2023-09-01T23:00:00.000+02:00";

    @Autowired
    private MockMvc mockMvc;
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
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetHourlyProduction() throws Exception {
        String authHeader = BasicAuthHeaderGenerator.generate(MockUser.USERNAME, MockUser.PASSWORD);

        mockMvc.perform(get("/api/v1/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testInvalidDateFormat() throws Exception {

        String invalidStartDate = "foo";
        String validEndDate = "2023-10-25T23:00:00.000+02:00";

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", invalidStartDate)
                        .queryParam("endDate", validEndDate))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString("\"message\":\"El campo con nombre 'startDate' y valor 'foo' tiene un formato incorrecto. El formato esperado es 'yyyy-mm-ddThh:mm:ss.000+h:mm'\"")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetHourlyProductionBySupply() throws Exception {

        // Create some supplies
        supplyRepository.saveAll(Arrays.asList(
                new SupplyEntity("1", "My house", "Fake street", 0.030763f),
                new SupplyEntity("2", "The garage", "Sesame Street 666", 0.015380f),
                new SupplyEntity("3", "My daughter's house", "Real street 22", 0.041017f)
        ));
        SupplyId supplyId = new SupplyId("1");

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("supplyId", supplyId.getId())
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetHourlyProductionByUnknownSupply() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("supplyId", supplyId)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString("\"message\":\"El punto de suministro con identificador '1' no has sido encontrado. Revise que el identificador sea correcto.\"")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetHourlyProductionWithWrongSupplyParameterName() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("supply", supplyId)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetHourlyProductionWithoutStartDate() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString("\"message\":\"El parámetro con nombre 'startDate' es obligatorio.\"")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetHourlyProductionWithoutEndDate() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().encoding(StandardCharsets.UTF_8))
                .andExpect(content().string(containsString("\"message\":\"El parámetro con nombre 'endDate' es obligatorio.\"")));
    }
}
