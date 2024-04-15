package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SetHuaweiConfigControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/production/huawei/config";

    @Autowired
    private HuaweiConfigRepository repository;
    @Autowired
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    @Test
    void testOverrideConfigureHuawei() throws Exception {
        // Assemble
        String testUsername = "testUsername";
        String testPassword = "testPassword";
        String modifier = "foo";

        setHuaweiConfigurationRepository.setHuaweiConfiguration(new HuaweiConfig.Builder().setUsername(testUsername).setPassword(testPassword).build());

        ConfigureHuaweiBody body = new ConfigureHuaweiBody(testUsername + modifier, testPassword + modifier);
        String bodyAsString = objectMapper.writeValueAsString(body);

        // Act
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(
                put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testUsername + modifier))
                .andExpect(jsonPath("$.passwordSet").isBoolean())
                .andExpect(jsonPath("$.passwordSet").value(true));

        Assertions.assertTrue(repository.findFirstByOrderByIdAsc().isPresent());
    }

    @Test
    void testNewConfigureHuawei() throws Exception {
        // Assemble
        String testUsername = "testUsername";
        String testPassword = "testPassword";
        ConfigureHuaweiBody body = new ConfigureHuaweiBody(testUsername, testPassword);
        String bodyAsString = objectMapper.writeValueAsString(body);

        // Act
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(
                        put(URL)
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testUsername))
                .andExpect(jsonPath("$.passwordSet").isBoolean())
                .andExpect(jsonPath("$.passwordSet").value(true));

        Assertions.assertTrue(repository.findFirstByOrderByIdAsc().isPresent());
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(put(URL)
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

        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}