package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInflux3Loader;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class GetSupplyDailyConsumptionControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";
    private static final String START_DATE = "2023-04-01T00:00:00Z";
    private static final String END_DATE = "2023-04-30T23:59:59Z";
    private static final String CUPS_CODE = "ES0031406912345678JN0F";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInflux3Loader datadisConsumptionInfluxLoader;

    @BeforeEach
    void beforeEach() {
        datadisConsumptionInfluxLoader.loadData();
    }

    @AfterEach
    void afterEach() {
        datadisConsumptionInfluxLoader.clearData();
    }

    @Test
    void testGetSupplyDailyConsumptionAsAdmin() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("consumptionKWh")))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].cups").value(CUPS_CODE));
    }

    @Test
    void testGetSupplyDailyConsumptionAsOwner() throws Exception {
        // Create a partner user
        User partnerUser = UserMother.randomUser();
        partnerUser.setRole(Role.PARTNER);
        partnerUser.enable();
        User createdPartnerUser = createUserRepository.create(partnerUser);

        // Create supply for this user
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(createdPartnerUser).withCode(CUPS_CODE).build(),
                UserId.of(createdPartnerUser.getId()));

        // Login as the partner user
        String partnerToken = loginUser(partnerUser);

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, partnerToken)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("consumptionKWh")))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].cups").value(CUPS_CODE));
    }

    @Test
    void testGetSupplyDailyConsumptionAsNonOwner() throws Exception {
        // Create a supply for one user
        User ownerUser = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(ownerUser).withCode(CUPS_CODE).build(),
                UserId.of(ownerUser.getId()));

        // Login as a different partner user
        String partnerToken = loginAsPartner();

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, partnerToken)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetSupplyDailyConsumptionWithMissingStartDate() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")));
    }

    @Test
    void testGetSupplyDailyConsumptionWithMissingEndDate() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().encoding(StandardCharsets.UTF_8));
    }

    @Test
    void testGetSupplyDailyConsumptionWithUnknownSupply() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        UUID supplyId = UUID.randomUUID();

        mockMvc.perform(get(URL + "/" + supplyId + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":404")))
                .andExpect(content().string(containsString(String.format("\"message\":\"El punto de suministro con identificador '%s' no ha sido encontrado. Revise que el identificador sea correcto.\"", supplyId))));
    }

    @Test
    void testWithMissingToken() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get(URL + "/" + randomId + "/consumption/daily")
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithWrongToken() throws Exception {
        UUID randomId = UUID.randomUUID();
        final String wrongToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX + "wrong";

        mockMvc.perform(get(URL + "/" + randomId + "/consumption/daily")
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE)
                        .header(HttpHeaders.AUTHORIZATION, wrongToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithExpiredToken() throws Exception {
        UUID randomId = UUID.randomUUID();
        final String expiredToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.jO3pgdDj4mg9TnRzL7f8RUL1ytJS7057jAg6zaCcwn0";

        mockMvc.perform(get(URL + "/" + randomId + "/consumption/daily")
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE)
                        .header(HttpHeaders.AUTHORIZATION, expiredToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
