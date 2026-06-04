package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

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

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SetDatadisConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/communities/%s/config/datadis";

    @Autowired
    private DatadisConfigRepository repository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    @Test
    void testOverrideConfigureDatadis() throws Exception {
        setDatadisConfigurationRepository.setDatadisConfiguration(DEFAULT_COMMUNITY_ID, new DatadisConfig.Builder()
                .setUsername("testUsername")
                .setPassword("testPassword")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.FALSE)
                .build());

        ConfigureDatadisBody body = new ConfigureDatadisBody("testUsernameNew", "testPasswordNew",
                DatadisConfig.DEFAULT_BASE_URL, Boolean.TRUE);

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(
                put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testUsernameNew"))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.baseUrl").value(DatadisConfig.DEFAULT_BASE_URL))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testNewConfigureDatadis() throws Exception {
        ConfigureDatadisBody body = new ConfigureDatadisBody("testUser", "testPass",
                DatadisConfig.DEFAULT_BASE_URL, Boolean.TRUE);

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(
                        put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testUser"))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.baseUrl").value(DatadisConfig.DEFAULT_BASE_URL))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
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
        mockMvc.perform(put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
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

        ConfigureDatadisBody body = new ConfigureDatadisBody("testUsername", "testPassword",
                DatadisConfig.DEFAULT_BASE_URL, Boolean.FALSE);

        mockMvc.perform(put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
