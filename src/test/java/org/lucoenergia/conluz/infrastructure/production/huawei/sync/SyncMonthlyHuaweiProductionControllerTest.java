package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiDisabledException;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncMonthlyHuaweiProductionControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/huawei/sync/monthly";

    @MockitoBean
    private HuaweiProductionMonthlyAggregationService aggregationService;

    @Autowired
    private ObjectMapper objectMapper;

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

        // Controller only forwards the request to the service, which owns all the dispatch logic
        verify(aggregationService, times(1))
                .syncMonthlyProductions(eq(DEFAULT_COMMUNITY_ID), isNull(), isNull(), eq(2024));
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
                .syncMonthlyProductions(eq(DEFAULT_COMMUNITY_ID), eq("PLANT001"), eq(6), eq(2024));
    }

    @Test
    void testWhenHuaweiDisabled_thenConflict() throws Exception {

        doThrow(new HuaweiDisabledException())
                .when(aggregationService)
                .syncMonthlyProductions(any(), any(), any(), anyInt());

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
    void testWithoutTokenReturnsUnauthorized() throws Exception {

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));

        verifyNoInteractions(aggregationService);
    }

    @Test
    void testMemberWhoIsNotAdminGetsForbidden() throws Exception {

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));

        verifyNoInteractions(aggregationService);
    }

    @Test
    void testUserWhoCannotSeeCommunityGetsNotFound() throws Exception {

        String authHeader = loginAsPartner();

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));

        verifyNoInteractions(aggregationService);
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

        verifyNoInteractions(aggregationService);
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

        verifyNoInteractions(aggregationService);
    }

    @Test
    void testWithInvalidPlantCodeReturnsNotFound() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyHuaweiProductionBody body = new SyncMonthlyHuaweiProductionBody(2024, 1, "INVALID");

        doThrow(new PlantNotFoundException("INVALID"))
                .when(aggregationService)
                .syncMonthlyProductions(any(), any(), any(), anyInt());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
