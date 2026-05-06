package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncYearlyDatadisConsumptionsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/consumption/datadis/sync/yearly";

    @MockitoBean
    private DatadisYearlyAggregationService aggregationService;

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
        when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.of(enabledConfig));
    }

    @Test
    void testAggregateYearlyForAllSupplies() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncYearlyDatadisConsumptionsBody body = new SyncYearlyDatadisConsumptionsBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .aggregateYearlyConsumptions(eq(2024));
    }

    @Test
    void testAggregateYearlyForSpecificSupply() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncYearlyDatadisConsumptionsBody body = new SyncYearlyDatadisConsumptionsBody(2024, "SUPPLY001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .aggregateYearlyConsumptions(eq(SupplyCode.of("SUPPLY001")), eq(2024));
    }

    @Test
    void testWithoutToken() throws Exception {

        SyncYearlyDatadisConsumptionsBody body = new SyncYearlyDatadisConsumptionsBody(2024);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        SyncYearlyDatadisConsumptionsBody body = new SyncYearlyDatadisConsumptionsBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void testWithInvalidYearTooLow() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncYearlyDatadisConsumptionsBody body = new SyncYearlyDatadisConsumptionsBody(1999);

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

        String authHeader = loginAsDefaultAdmin();

        SyncYearlyDatadisConsumptionsBody body = new SyncYearlyDatadisConsumptionsBody(2101);

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

        String authHeader = loginAsDefaultAdmin();

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
    void testWithInvalidSupplyCode() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncYearlyDatadisConsumptionsBody body = new SyncYearlyDatadisConsumptionsBody(2024, "INVALID");

        doThrow(new SupplyNotFoundException(SupplyCode.of("INVALID")))
                .when(aggregationService)
                .aggregateYearlyConsumptions(any(SupplyCode.class), any(Integer.class));

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
