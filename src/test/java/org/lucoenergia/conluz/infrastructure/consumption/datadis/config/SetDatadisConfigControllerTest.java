package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
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
class SetDatadisConfigControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/consumption/datadis/config";

    @Autowired
    private DatadisConfigRepository repository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    @Test
    void testOverrideConfigureDatadis() throws Exception {
        // Assemble
        String testUsername = "testUsername";
        String testPassword = "testPassword";
        String modifier = "foo";

        setDatadisConfigurationRepository.setDatadisConfiguration(new DatadisConfig(testUsername, testPassword));

        ConfigureDatadisBody body = new ConfigureDatadisBody(testUsername + modifier, testPassword + modifier);
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
    void testNewConfigureDatadis() throws Exception {
        // Assemble
        String testUsername = "testUsername";
        String testPassword = "testPassword";
        ConfigureDatadisBody body = new ConfigureDatadisBody(testUsername, testPassword);
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
    void
    testWithoutBody() throws Exception {
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

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        String testUsername = "testUsername";
        String testPassword = "testPassword";
        ConfigureDatadisBody body = new ConfigureDatadisBody(testUsername, testPassword);
        String bodyAsString = objectMapper.writeValueAsString(body);

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}