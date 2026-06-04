package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.SetShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetShellyConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/communities/%s/config/shelly";

    @Autowired
    private SetShellyConfigRepository setShellyConfigRepository;

    @Test
    void testGetConfigWhenExists() throws Exception {
        setShellyConfigRepository.setShellyConfiguration(DEFAULT_COMMUNITY_ID, new ShellyConfig.Builder()
                .setId(UUID.randomUUID())
                .setEnabled(Boolean.TRUE)
                .build());

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(
                        get(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").isBoolean())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testGetConfigWhenNotExists() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(
                        get(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void testWithoutToken() throws Exception {
        mockMvc.perform(get(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithNonAdminRole() throws Exception {
        String authHeader = loginAsPartner();

        mockMvc.perform(
                        get(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
