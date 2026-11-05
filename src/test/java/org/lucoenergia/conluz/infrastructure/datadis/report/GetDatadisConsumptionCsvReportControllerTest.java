package org.lucoenergia.conluz.infrastructure.datadis.report;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class GetDatadisConsumptionCsvReportControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID
            + "/consumption/datadis/report/hourly/csv";
    private static final String START_DATE = "2023-04-01T00:00:00Z";
    private static final String END_DATE = "2023-04-30T23:59:59Z";
    private static final String CUPS_CODE = "ES0031406912345678JN0F";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInfluxLoader datadisConsumptionInfluxLoader;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;

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
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

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
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("\"cups\",\"date\",\"time\",\"consumptionKWh\",\"obtainMethod\",\"surplusEnergyKWh\",\"generationEnergyKWh\",\"selfConsumptionEnergyKWh\"")));
    }

    /**
     * The CSV is a Datadis pass-through and keeps the Datadis names, unlike the consumption series
     * (#460): its first line is exactly the header it always had.
     */
    @Test
    @DisplayName("EVL-003 AC6 the CSV report keeps the Datadis header")
    void testGetCsvReportKeepsTheDatadisHeader() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        User user = createUserRepository.create(UserMother.randomUser());
        createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        String csv = mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals("\"cups\",\"date\",\"time\",\"consumptionKWh\",\"obtainMethod\",\"surplusEnergyKWh\","
                        + "\"generationEnergyKWh\",\"selfConsumptionEnergyKWh\"",
                csv.lines().findFirst().orElseThrow());
    }

    /**
     * Pins the report's whole output for a fixed dataset, so that a change to the read path it shares
     * with the supply hourly series cannot alter it unnoticed (#545). The dataset holds what tells the
     * bound modes apart: a record exactly on {@code endDate}, which the report includes because it reads
     * both bounds inclusively, and an hour with no record, which it reports as zeros without an obtain
     * method.
     *
     * <p>The community's supplies are read in no particular order, so either supply's rows may come
     * first; each order is compared byte for byte.
     */
    @Test
    @DisplayName("#545 AC6 the CSV report output for a fixed dataset is exactly what it was")
    void testGetCsvReportOutputIsUnchangedForAFixedDataset() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(community.getId());
        User user = createUserRepository.create(UserMother.randomUser());
        Supply first = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        Supply second = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        try {
            influxFixture.writeAt(first.getCode(), OffsetDateTime.parse("2023-06-10T10:00:00+02:00").toInstant(),
                    1.5f, 0.25f, 0.5f);
            influxFixture.writeAt(first.getCode(), OffsetDateTime.parse("2023-06-10T12:00:00+02:00").toInstant(),
                    2.25f, 0f, 0f);
            influxFixture.writeAt(first.getCode(), OffsetDateTime.parse("2023-06-10T13:00:00+02:00").toInstant(),
                    4f, 1f, 0.125f);
            influxFixture.writeAt(second.getCode(), OffsetDateTime.parse("2023-06-10T11:00:00+02:00").toInstant(),
                    0.75f, 0.1f, 0.2f);

            String csv = mockMvc.perform(get("/api/v1/communities/" + community.getId()
                            + "/consumption/datadis/report/hourly/csv")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .queryParam("startDate", "2023-06-10T10:00:00+02:00")
                            .queryParam("endDate", "2023-06-10T13:00:00+02:00"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String header = "\"cups\",\"date\",\"time\",\"consumptionKWh\",\"obtainMethod\",\"surplusEnergyKWh\","
                    + "\"generationEnergyKWh\",\"selfConsumptionEnergyKWh\"\n";
            String firstRows = row(first, "10:00", "1.5", "\"Real\"", "0.5", "0.25")
                    + row(first, "11:00", "0.0", "", "0.0", "0.0")
                    + row(first, "12:00", "2.25", "\"Real\"", "0.0", "0.0")
                    + row(first, "13:00", "4.0", "\"Real\"", "0.125", "1.0");
            String secondRows = row(second, "10:00", "0.0", "", "0.0", "0.0")
                    + row(second, "11:00", "0.75", "\"Real\"", "0.2", "0.1")
                    + row(second, "12:00", "0.0", "", "0.0", "0.0")
                    + row(second, "13:00", "0.0", "", "0.0", "0.0");

            assertTrue(csv.equals(header + firstRows + secondRows) || csv.equals(header + secondRows + firstRows),
                    () -> "unexpected CSV:\n" + csv);
        } finally {
            influxFixture.clear(first.getCode());
            influxFixture.clear(second.getCode());
        }
    }

    private static String row(Supply supply, String time, String consumptionKWh, String obtainMethod,
                              String surplusEnergyKWh, String selfConsumptionEnergyKWh) {
        return "\"" + supply.getCode() + "\",\"2023/06/10\",\"" + time + "\",\"" + consumptionKWh + "\","
                + obtainMethod + ",\"" + surplusEnergyKWh + "\",\"0.0\",\"" + selfConsumptionEnergyKWh + "\"\n";
    }

    @Test
    void testGetCsvReportAsPartnerIsNotFound() throws Exception {
        String partnerToken = loginAsPartner();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, partnerToken)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetCsvReportWithMissingStartDate() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"status\":400")));
    }

    @Test
    void testGetCsvReportWithMissingEndDate() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

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
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

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
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.xvJF4LjS7oIcMUXjI7WbHkuxTnmuJn-3JVcwWm6qbok";

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
