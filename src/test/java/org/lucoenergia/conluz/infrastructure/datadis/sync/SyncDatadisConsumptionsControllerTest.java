package org.lucoenergia.conluz.infrastructure.datadis.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SyncDatadisConsumptionsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/consumption/datadis/sync";

    /** Thursday 2026-11-26 in any JVM default zone from UTC-11 to UTC+11. */
    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    /**
     * The controller only hands the request over: the real service is not run. The clock is moved
     * before any login, so the tokens are issued at the new instant.
     */
    @BeforeEach
    void serviceDoesNothing() {
        doNothing().when(datadisSyncService).synchronize(any(), any(), any(), (String) any());
        travelTo(NOW);
        assertEquals(TODAY, LocalDate.ofInstant(NOW, ZoneId.systemDefault()));
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("DHI-008 monthsBack 0 synchronizes every supply from the first day of the current month through today")
    void zeroMonthsBackSynchronizesTheCurrentMonth() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsBack\": 0}"))
                .andDo(print())
                .andExpect(status().isOk());

        // Controller only forwards the request; the service owns config gating and supply dispatch
        verify(datadisSyncService, times(1))
                .synchronize(eq(DEFAULT_COMMUNITY_ID), eq(LocalDate.of(2026, 11, 1)), eq(TODAY), (String) isNull());
        verifyNoMoreInteractions(datadisSyncService);
    }

    @Test
    @DisplayName("DHI-008 monthsBack 24 synchronizes every supply from the first day of the month 24 months back through today")
    void twentyFourMonthsBackSynchronizesTwoYears() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsBack\": 24}"))
                .andDo(print())
                .andExpect(status().isOk());

        verify(datadisSyncService, times(1))
                .synchronize(eq(DEFAULT_COMMUNITY_ID), eq(LocalDate.of(2024, 11, 1)), eq(TODAY), (String) isNull());
        verifyNoMoreInteractions(datadisSyncService);
    }

    @Test
    @DisplayName("DHI-008 with a supplyCode only that supply is synchronized, over the same range")
    void testSyncDatadisConsumptionsWithValidSupplyCode() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(3, "SUPPLY001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(datadisSyncService, times(1))
                .synchronize(eq(DEFAULT_COMMUNITY_ID), eq(LocalDate.of(2026, 8, 1)), eq(TODAY), eq("SUPPLY001"));
        verifyNoMoreInteractions(datadisSyncService);
    }

    @Test
    void testWithoutTokenReturnsUnauthorized() throws Exception {

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(3);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));

        verifyNoInteractions(datadisSyncService);
    }

    @Test
    void testMemberWhoIsNotAdminGetsForbidden() throws Exception {

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(3);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));

        verifyNoInteractions(datadisSyncService);
    }

    @Test
    void testUserWhoCannotSeeCommunityGetsNotFound() throws Exception {

        String authHeader = loginAsPartner();

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(3);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));

        verifyNoInteractions(datadisSyncService);
    }

    @Test
    @DisplayName("DHI-006 the endpoint answers 409 when the service refuses a disabled configuration")
    void testWithDatadisDisabledReturnsConflict() throws Exception {

        doThrow(new DatadisDisabledException())
                .when(datadisSyncService)
                .synchronize(any(UUID.class), any(LocalDate.class), any(LocalDate.class), nullable(String.class));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(3);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DHI-006 the 409 message names the operation that enables the configuration")
    void testWithDatadisDisabledMessageNamesTheEnableAction() throws Exception {

        doThrow(new DatadisDisabledException())
                .when(datadisSyncService)
                .synchronize(any(UUID.class), any(LocalDate.class), any(LocalDate.class), nullable(String.class));

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SyncDatadisConsumptionsBody(3))))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        containsString("POST /api/v1/communities/{communityId}/config/datadis/enable")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"monthsBack\": -1}",
            "{\"monthsBack\": 25}",
            "{\"monthsBack\": null}",
            "{}",
            "{\"supplyCode\": \"SUPPLY001\"}",
            "{\"year\": 2025}",
            "{\"monthsBack\": 3, \"year\": 2025}"
    })
    @DisplayName("DHI-008 a monthsBack out of 0 to 24, a missing one or the former year field is answered 400 and Datadis is not called")
    void anInvalidBodyIsRefusedBeforeCallingDatadis(String content) throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));

        verifyNoInteractions(datadisSyncService);
    }

    @Test
    void testWithMissingBody() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));

        verifyNoInteractions(datadisSyncService);
    }

    @Test
    void testSyncDatadisConsumptionsWithInvalidSupplyCode() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(3, "INVALID");

        doThrow(new SupplyNotFoundException(SupplyCode.of("INVALID")))
                .when(datadisSyncService)
                .synchronize(any(UUID.class), any(LocalDate.class), any(LocalDate.class), nullable(String.class));

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
