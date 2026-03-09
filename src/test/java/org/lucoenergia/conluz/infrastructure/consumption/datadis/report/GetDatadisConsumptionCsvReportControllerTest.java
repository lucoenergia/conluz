package org.lucoenergia.conluz.infrastructure.consumption.datadis.report;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class GetDatadisConsumptionCsvReportControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/consumption/datadis/report/hourly/csv";
    private static final String START_DATE = "2023-04-01T00:00:00Z";
    private static final String END_DATE = "2023-04-30T23:59:59Z";
    private static final String CUPS_CODE = "ES0031406912345678JN0F";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInfluxLoader datadisConsumptionInfluxLoader;

    @BeforeEach
    void beforeEach() {
        datadisConsumptionInfluxLoader.loadData();
    }

    @AfterEach
    void afterEach() {
        datadisConsumptionInfluxLoader.clearData();
    }

    @Test
    void testGetCsvReportAsAdmin() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        User user = createUserRepository.create(UserMother.randomUser());
        createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("\"cups\",\"date\",\"time\",\"consumptionKWh\",\"obtainMethod\",\"surplusEnergyKWh\",\"generationEnergyKWh\",\"selfConsumptionEnergyKWh\"")))
                .andExpect(content().string(containsString(CUPS_CODE)));
    }

    @Test
    void testGetCsvReportWithNoSuppliesReturnsOnlyHeader() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("\"cups\",\"date\",\"time\",\"consumptionKWh\",\"obtainMethod\",\"surplusEnergyKWh\",\"generationEnergyKWh\",\"selfConsumptionEnergyKWh\"")));
    }

    @Test
    void testGetCsvReportAsPartnerIsForbidden() throws Exception {
        String partnerToken = loginAsPartner();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, partnerToken)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetCsvReportWithMissingStartDate() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"status\":400")));
    }

    @Test
    void testGetCsvReportWithMissingEndDate() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"status\":400")));
    }

    @Test
    void testGetCsvReportWithMissingToken() throws Exception {
        mockMvc.perform(get(URL)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetCsvReportWithWrongToken() throws Exception {
        final String wrongToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX + "wrong";

        mockMvc.perform(get(URL)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE)
                        .header(HttpHeaders.AUTHORIZATION, wrongToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testTimezoneFilteringIncludesMayData() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        User user = createUserRepository.create(UserMother.randomUser());
        createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2023-04-01T00:00:00Z")
                        .queryParam("endDate", "2023-05-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("2023/05/01")))
                .andExpect(content().string(containsString("2023/04/30")));
    }

    @Test
    void testGetCsvReportWithExpiredToken() throws Exception {
        final String expiredToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.jO3pgdDj4mg9TnRzL7f8RUL1ytJS7057jAg6zaCcwn0";

        mockMvc.perform(get(URL)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE)
                        .header(HttpHeaders.AUTHORIZATION, expiredToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
