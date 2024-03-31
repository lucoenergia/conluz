package org.lucoenergia.conluz.infrastructure.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetInstantProductionControllerTest extends BaseControllerTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private EnergyProductionInfluxLoader energyProductionInfluxLoader;

    @BeforeEach
    void beforeEach() {
        energyProductionInfluxLoader.loadData();
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

        UserEntity user = UserMother.randomUserEntity();
        user = userRepository.save(user);

        String authHeader = loginAsDefaultAdmin();
        UUID supplyId = UUID.randomUUID();
        SupplyEntity supplyEntity = SupplyEntityMother.random(user);
        supplyEntity.setId(supplyId);

        // Create some supplies
        supplyRepository.saveAll(Arrays.asList(
                supplyEntity,
                SupplyEntityMother.random(user),
                SupplyEntityMother.random(user)
        ));

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supplyId", supplyId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    void testGetInstantProductionByUnknownSupply() throws Exception {

        String authHeader = loginAsDefaultAdmin();
        UUID supplyId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supplyId", supplyId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString(String.format("\"message\":\"El punto de suministro con identificador '%s' no ha sido encontrado. Revise que el identificador sea correcto.\"", supplyId))));
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
