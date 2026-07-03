package org.lucoenergia.conluz.infrastructure.production.datadis.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationService;
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

class SyncYearlyDatadisProductionControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/datadis/sync/yearly";

    @MockitoBean
    private DatadisProductionYearlyAggregationService aggregationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testAggregateYearlyForAllSupplies() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncYearlyDatadisProductionBody body = new SyncYearlyDatadisProductionBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        // Controller only forwards the request to the service, which owns all the dispatch logic
        verify(aggregationService, times(1))
                .syncYearlyProductions(eq(DEFAULT_COMMUNITY_ID), isNull(), eq(2024));
    }

    @Test
    void testAggregateYearlyForSpecificSupply() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncYearlyDatadisProductionBody body = new SyncYearlyDatadisProductionBody(2024, "SUPPLY001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .syncYearlyProductions(eq(DEFAULT_COMMUNITY_ID), eq("SUPPLY001"), eq(2024));
    }

    @Test
    void testWithoutTokenReturnsUnauthorized() throws Exception {

        SyncYearlyDatadisProductionBody body = new SyncYearlyDatadisProductionBody(2024);

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

        SyncYearlyDatadisProductionBody body = new SyncYearlyDatadisProductionBody(2024);

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

        SyncYearlyDatadisProductionBody body = new SyncYearlyDatadisProductionBody(2024);

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
                .syncYearlyProductions(any(), any(), anyInt());

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncYearlyDatadisProductionBody body = new SyncYearlyDatadisProductionBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void testWithInvalidYearTooHigh() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncYearlyDatadisProductionBody body = new SyncYearlyDatadisProductionBody(2101);

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

        verifyNoInteractions(aggregationService);
    }

    @Test
    void testWithInvalidSupplyCodeReturnsNotFound() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncYearlyDatadisProductionBody body = new SyncYearlyDatadisProductionBody(2024, "INVALID");

        doThrow(new SupplyNotFoundException(SupplyCode.of("INVALID")))
                .when(aggregationService)
                .syncYearlyProductions(any(), any(), anyInt());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
