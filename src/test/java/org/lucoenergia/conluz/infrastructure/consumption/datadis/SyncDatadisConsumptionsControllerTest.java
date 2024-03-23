package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumptionSyncService;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncDatadisConsumptionsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/consumption/datadis/sync";

    @MockBean
    private DatadisConsumptionSyncService datadisConsumptionSyncService;

    @Test
    void testSyncDatadisConsumptions() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());

        verify(datadisConsumptionSyncService, times(1)).synchronizeConsumptions();
    }

    @Test
    void testWithoutToken() throws Exception {

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}