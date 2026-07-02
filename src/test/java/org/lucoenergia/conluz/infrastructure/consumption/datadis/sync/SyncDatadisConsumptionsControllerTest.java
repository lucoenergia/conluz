package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncDatadisConsumptionsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/consumption/datadis/sync";

    @MockitoBean
    private DatadisSyncService datadisSyncService;

    @MockitoBean
    private GetDatadisConfigRepository getDatadisConfigRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setupEnabledConfig() {
        DatadisConfig enabledConfig = new DatadisConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build();
        when(getDatadisConfigRepository.findByCommunityId(DEFAULT_COMMUNITY_ID)).thenReturn(Optional.of(enabledConfig));
    }

    @Test
    void testSyncDatadisConsumptions() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(datadisSyncService, times(1))
                .synchronize(
                        eq(DEFAULT_COMMUNITY_ID),
                        eq(LocalDate.of(2024, 1, 1)),
                        eq(LocalDate.of(2024, 12, 31))
                );
    }

    @Test
    void testWithoutToken() throws Exception {

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2024);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleGetsNotFound() throws Exception {

        String authHeader = loginAsPartner();

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2024);

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

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(1999);

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

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2101);

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
    void testWithMissingYear() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String jsonBody = "{}";

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
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
    }

    @Test
    void testSyncDatadisConsumptionsWithValidSupplyCode() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2024, "SUPPLY001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(datadisSyncService, times(1))
                .synchronize(
                        eq(DEFAULT_COMMUNITY_ID),
                        eq(LocalDate.of(2024, 1, 1)),
                        eq(LocalDate.of(2024, 12, 31)),
                        eq(SupplyCode.of("SUPPLY001"))
                );

        verify(datadisSyncService, never())
                .synchronize(any(UUID.class), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void testSyncDatadisConsumptionsWithInvalidSupplyCode() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2024, "INVALID");

        doThrow(new SupplyNotFoundException(SupplyCode.of("INVALID")))
                .when(datadisSyncService)
                .synchronize(
                        any(UUID.class),
                        any(LocalDate.class),
                        any(LocalDate.class),
                        any(SupplyCode.class)
                );

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void testSyncDatadisConsumptionsWithNullSupplyCode() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2024, null);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(datadisSyncService, times(1))
                .synchronize(
                        eq(DEFAULT_COMMUNITY_ID),
                        eq(LocalDate.of(2024, 1, 1)),
                        eq(LocalDate.of(2024, 12, 31))
                );

        verify(datadisSyncService, never())
                .synchronize(any(UUID.class), any(LocalDate.class), any(LocalDate.class), any(SupplyCode.class));
    }

    @Test
    void testSyncDatadisConsumptionsWithEmptySupplyCode() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2024, "");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(datadisSyncService, times(1))
                .synchronize(
                        eq(DEFAULT_COMMUNITY_ID),
                        eq(LocalDate.of(2024, 1, 1)),
                        eq(LocalDate.of(2024, 12, 31))
                );

        verify(datadisSyncService, never())
                .synchronize(any(UUID.class), any(LocalDate.class), any(LocalDate.class), any(SupplyCode.class));
    }

    @Test
    void testSyncDatadisConsumptionsWithBlankSupplyCode() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(2024, "   ");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(datadisSyncService, times(1))
                .synchronize(
                        eq(DEFAULT_COMMUNITY_ID),
                        eq(LocalDate.of(2024, 1, 1)),
                        eq(LocalDate.of(2024, 12, 31))
                );

        verify(datadisSyncService, never())
                .synchronize(any(UUID.class), any(LocalDate.class), any(LocalDate.class), any(SupplyCode.class));
    }
}
