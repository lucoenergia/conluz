package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
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

class SyncMonthlyDatadisConsumptionsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/consumption/datadis/sync/monthly";

    @MockitoBean
    private DatadisMonthlyAggregationService aggregationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testAggregateMonthlyForAllSuppliesAllMonths() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        // Controller only forwards the request to the service, which owns all the dispatch logic
        verify(aggregationService, times(1))
                .syncMonthlyConsumptions(eq(DEFAULT_COMMUNITY_ID), isNull(), isNull(), eq(2024));
    }

    @Test
    void testAggregateMonthlyForAllSuppliesSpecificMonth() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024, 1);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .syncMonthlyConsumptions(eq(DEFAULT_COMMUNITY_ID), isNull(), eq(1), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificSupplySpecificMonth() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024, 6, "SUPPLY001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .syncMonthlyConsumptions(eq(DEFAULT_COMMUNITY_ID), eq("SUPPLY001"), eq(6), eq(2024));
    }

    @Test
    void testWithoutTokenReturnsUnauthorized() throws Exception {

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024);

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

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024);

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

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024);

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
    void testWithDatadisDisabledReturnsConflict() throws Exception {

        doThrow(new DatadisDisabledException())
                .when(aggregationService)
                .syncMonthlyConsumptions(any(), any(), any(), anyInt());

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void testWithInvalidYearTooLow() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(1999);

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
    void testWithInvalidSupplyCodeReturnsNotFound() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024, 1, "INVALID");

        doThrow(new SupplyNotFoundException(SupplyCode.of("INVALID")))
                .when(aggregationService)
                .syncMonthlyConsumptions(any(), any(), any(), anyInt());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
