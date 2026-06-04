package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SetHuaweiConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/production/huawei/config/%s";

    @Autowired
    private HuaweiConfigRepository huaweiConfigRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    void testNewConfigureHuawei() throws Exception {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));
        Plant plant = PlantMother.random(supply).build();
        plant = createPlantRepository.create(plant, SupplyId.of(supply.getId()));

        ConfigureHuaweiBody body = new ConfigureHuaweiBody("testUser", "testPass",
                HuaweiConfig.DEFAULT_BASE_URL, Boolean.TRUE);
        String bodyAsString = objectMapper.writeValueAsString(body);

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(
                        put(String.format(URL_TEMPLATE, plant.getId()))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testUser"))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.baseUrl").value(HuaweiConfig.DEFAULT_BASE_URL))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(put(String.format(URL_TEMPLATE, UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {
        mockMvc.perform(put(String.format(URL_TEMPLATE, UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}