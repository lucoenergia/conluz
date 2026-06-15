package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncMonthlyHuaweiProductionControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/huawei/sync/monthly";

    @MockitoBean
    private HuaweiProductionMonthlyAggregationService aggregationService;

    @MockitoBean
    private GetHuaweiConfigRepository getHuaweiConfigRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setupEnabledConfig() {
        HuaweiConfig enabledConfig = new HuaweiConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(enabledConfig));
    }

    @Test
    void testAggregateMonthlyForAllPlantsAllMonths() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .aggregateMonthlyProductions(eq(DEFAULT_COMMUNITY_ID), eq(2024));
    }

    @Test
    void testAggregateMonthlyForAllPlantsSpecificMonth() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024, 1);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .aggregateMonthlyProductions(eq(DEFAULT_COMMUNITY_ID), eq(Month.JANUARY), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificPlantAllMonths() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024, null, "PLANT001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        // Should call for all 12 months
        verify(aggregationService, times(12))
                .aggregateMonthlyProductions(eq(DEFAULT_COMMUNITY_ID), eq("PLANT001"), any(Month.class), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificPlantSpecificMonth() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024, 6, "PLANT001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .aggregateMonthlyProductions(eq(DEFAULT_COMMUNITY_ID), eq("PLANT001"), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testWhenHuaweiDisabled_thenConflict() throws Exception {

        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()));
    }

    @Test
    void testWithoutToken() throws Exception {

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleGetsNotFound() throws Exception {

        String authHeader = loginAsPartner();

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void testWithInvalidYearTooLow() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(1999);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void testWithInvalidYearTooHigh() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2101);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void testWithNullYear() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String jsonBody = "{\"year\": null}";

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void testWithInvalidMonthTooLow() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String jsonBody = "{\"year\": 2024, \"month\": 0}";

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void testWithInvalidMonthTooHigh() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String jsonBody = "{\"year\": 2024, \"month\": 13}";

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void testWithInvalidPlantCode() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024, 1, "INVALID");

        doThrow(new PlantNotFoundException("INVALID"))
                .when(aggregationService)
                .aggregateMonthlyProductions(any(UUID.class), anyString(), any(Month.class), any(Integer.class));

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
