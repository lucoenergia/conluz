package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Month;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncMonthlyDatadisConsumptionsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/consumption/datadis/sync/monthly";

    @MockitoBean
    private DatadisMonthlyAggregationService aggregationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testAggregateMonthlyForAllSuppliesAllMonths() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .aggregateMonthlyConsumptions(eq(2024));
    }

    @Test
    void testAggregateMonthlyForAllSuppliesSpecificMonth() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024, 1);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .aggregateMonthlyConsumptions(eq(Month.JANUARY), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificSupplyAllMonths() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024, null, "SUPPLY001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        // Should call for all 12 months
        verify(aggregationService, times(12))
                .aggregateMonthlyConsumptions(any(SupplyCode.class), any(Month.class), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificSupplySpecificMonth() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024, 6, "SUPPLY001");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(aggregationService, times(1))
                .aggregateMonthlyConsumptions(eq(SupplyCode.of("SUPPLY001")), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testWithoutToken() throws Exception {

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024);

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

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024);

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

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(1999);

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

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2101);

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
    void testWithInvalidMonthTooLow() throws Exception {

        String authHeader = loginAsDefaultAdmin();

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

        String authHeader = loginAsDefaultAdmin();

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
    void testWithInvalidSupplyCode() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        SyncMonthlyDatadisConsumptionsBody body = new SyncMonthlyDatadisConsumptionsBody(2024, 1, "INVALID");

        doThrow(new SupplyNotFoundException(SupplyCode.of("INVALID")))
                .when(aggregationService)
                .aggregateMonthlyConsumptions(any(SupplyCode.class), any(Month.class), any(Integer.class));

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
