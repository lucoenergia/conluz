package org.lucoenergia.conluz.infrastructure.production;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.security.BasicAuthHeaderGenerator;
import org.lucoenergia.conluz.infrastructure.shared.security.MockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class GetInstantProductionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupplyRepository supplyRepository;

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetInstantProduction() throws Exception {
        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetInstantProductionBySupply() throws Exception {

        // Create some supplies
        supplyRepository.saveAll(Arrays.asList(
                new SupplyEntity("1", "My house", "Fake street", 0.030763f),
                new SupplyEntity("2", "The garage", "Sesame Street 666", 0.015380f),
                new SupplyEntity("3", "My daughter's house", "Real street 22", 0.041017f)
        ));

        String authHeader = BasicAuthHeaderGenerator.generate();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supplyId", supplyId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetInstantProductionByUnknownSupply() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supplyId", supplyId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString("\"message\":\"El punto de suministro con identificador '1' no has sido encontrado. Revise que el identificador sea correcto.\"")));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetInstantProductionWithWrongParameter() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supply", supplyId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }
}
