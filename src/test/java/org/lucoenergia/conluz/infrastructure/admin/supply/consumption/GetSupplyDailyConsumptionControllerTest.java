package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.SupplyConsumptionSavingsInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetSupplyDailyConsumptionControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";
    private static final String START_DATE = "2023-04-01T00:00:00Z";
    private static final String END_DATE = "2023-04-30T23:59:59Z";
    private static final String CUPS_CODE = "ES0031406912345678JN0F";
    private static final String SAVINGS_START_DATE = "2023-04-10T00:00:00+02:00";
    private static final String SAVINGS_END_DATE = "2023-04-14T23:59:59+02:00";
    private static final double TOLERANCE = 0.0001;

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInfluxLoader datadisConsumptionInfluxLoader;
    @Autowired
    private SupplyConsumptionSavingsInfluxLoader supplyConsumptionSavingsInfluxLoader;

    @BeforeEach
    void beforeEach() {
        datadisConsumptionInfluxLoader.loadData();
        supplyConsumptionSavingsInfluxLoader.loadData();
    }

    @AfterEach
    void afterEach() {
        datadisConsumptionInfluxLoader.clearData();
        supplyConsumptionSavingsInfluxLoader.clearData();
    }

    @Test
    void testGetSupplyDailyConsumptionAsAdmin() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

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
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetSupplyDailyConsumptionWithMissingStartDate() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

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
        String authHeader = loginAsDefaultPlatformAdmin();

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
        String authHeader = loginAsDefaultPlatformAdmin();
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
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.xvJF4LjS7oIcMUXjI7WbHkuxTnmuJn-3JVcwWm6qbok";

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

    /**
     * AC3. Every field the endpoint declared before the response DTO was introduced keeps its name
     * and its JSON type, so the only contract change on this endpoint is the one that is intended.
     */
    @Test
    void testDailyConsumptionKeepsEveryPreviouslyDeclaredField() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cups").isString())
                .andExpect(jsonPath("$[0].date").isString())
                .andExpect(jsonPath("$[0].time").isString())
                .andExpect(jsonPath("$[0].consumptionKWh").isNumber())
                .andExpect(jsonPath("$[0].obtainMethod").isString())
                .andExpect(jsonPath("$[0].surplusEnergyKWh").isNumber())
                .andExpect(jsonPath("$[0].generationEnergyKWh").isNumber())
                .andExpect(jsonPath("$[0].selfConsumptionEnergyKWh").isNumber());
    }

    /**
     * AC3. Asserted on the raw JSON rather than with a jsonPath absence matcher: `empty` was never
     * data, only the serialisation of an `isEmpty()` getter, and the point is that the key does not
     * reach the wire at all.
     */
    @Test
    void testDailyConsumptionNoLongerCarriesTheEmptyField() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"cups\"")))
                .andExpect(content().string(not(containsString("\"empty\""))));
    }

    /**
     * AC1. Five consecutive days with five different self-consumption figures, priced at the
     * estimated 0.15 EUR/kWh: 2.00, 0.50, 0.00, nothing at all and 1.50 kWh are worth 0.30, 0.075,
     * 0, 0 and 0.225, which present as 0.30, 0.08, 0.00, 0.00 and 0.23.
     *
     * <p>The 11th and the 14th land exactly on a half cent, so they only come out at 0.08 and 0.23
     * under HALF_UP; the raw-string assertions pin the scale, which a numeric comparison cannot.
     */
    @Test
    void testDailyConsumptionReportsEstimatedSavingsPerDay() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(SupplyConsumptionSavingsInfluxLoader.CUPS_WITH_SAVINGS).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", SAVINGS_START_DATE)
                        .queryParam("endDate", SAVINGS_END_DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].date").value("2023/04/10"))
                .andExpect(jsonPath("$[0].selfConsumptionEnergyKWh").value(closeTo(2.00, TOLERANCE)))
                .andExpect(jsonPath("$[0].savingsEur").value(closeTo(0.30, TOLERANCE)))
                .andExpect(jsonPath("$[1].date").value("2023/04/11"))
                .andExpect(jsonPath("$[1].selfConsumptionEnergyKWh").value(closeTo(0.50, TOLERANCE)))
                .andExpect(jsonPath("$[1].savingsEur").value(closeTo(0.08, TOLERANCE)))
                .andExpect(jsonPath("$[2].date").value("2023/04/12"))
                .andExpect(jsonPath("$[2].savingsEur").value(closeTo(0.00, TOLERANCE)))
                .andExpect(jsonPath("$[3].date").value("2023/04/13"))
                .andExpect(jsonPath("$[3].savingsEur").value(closeTo(0.00, TOLERANCE)))
                .andExpect(jsonPath("$[4].date").value("2023/04/14"))
                .andExpect(jsonPath("$[4].selfConsumptionEnergyKWh").value(closeTo(1.50, TOLERANCE)))
                .andExpect(jsonPath("$[4].savingsEur").value(closeTo(0.23, TOLERANCE)))
                .andExpect(jsonPath("$[0].tariffSource").value("ESTIMATE"))
                .andExpect(jsonPath("$[4].tariffSource").value("ESTIMATE"))
                .andExpect(content().string(containsString("\"savingsEur\":0.30")))
                .andExpect(content().string(containsString("\"savingsEur\":0.08")))
                .andExpect(content().string(containsString("\"savingsEur\":0.23")));
    }

    /**
     * AC4. Both zero cases, asserted on the raw JSON so the scale is part of what is checked: a day
     * whose records carry no self-consumption, and a day with no record at all. The endpoint does
     * not distinguish them -- `coverage` on the energy metrics endpoint is what tells absence from
     * a real zero.
     */
    @Test
    void testDailyConsumptionReportsZeroSavingsForDaysWithoutSelfConsumption() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(SupplyConsumptionSavingsInfluxLoader.CUPS_WITH_SAVINGS).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", SAVINGS_START_DATE)
                        .queryParam("endDate", SAVINGS_END_DATE))
                .andExpect(status().isOk())
                // The 12th has records and no self-consumption; the 13th has no record at all.
                .andExpect(jsonPath("$[2].obtainMethod").value("Real"))
                .andExpect(jsonPath("$[3].obtainMethod").value(nullValue()))
                .andExpect(content().string(containsString("\"date\":\"2023/04/12\"")))
                .andExpect(content().string(containsString("\"date\":\"2023/04/13\"")))
                .andExpect(content().string(containsString("\"savingsEur\":0.00")));
    }

    /**
     * AC9. Bounds in the middle of a local day: the first and last buckets carry only the hours
     * inside the range, and their savings must cover exactly those hours -- 1.75 kWh worth 0.2625
     * on the 10th from midday, and 1.25 kWh worth 0.1875 on the 14th up to 10:30.
     */
    @Test
    void testPartialEdgeDaysArePricedOverTheHoursTheyReport() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(SupplyConsumptionSavingsInfluxLoader.CUPS_WITH_SAVINGS).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2023-04-10T12:00:00+02:00")
                        .queryParam("endDate", "2023-04-14T10:30:00+02:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2023/04/10"))
                .andExpect(jsonPath("$[0].selfConsumptionEnergyKWh").value(closeTo(1.75, TOLERANCE)))
                .andExpect(jsonPath("$[0].savingsEur").value(closeTo(0.26, TOLERANCE)))
                .andExpect(jsonPath("$[4].date").value("2023/04/14"))
                .andExpect(jsonPath("$[4].selfConsumptionEnergyKWh").value(closeTo(1.25, TOLERANCE)))
                .andExpect(jsonPath("$[4].savingsEur").value(closeTo(0.19, TOLERANCE)));
    }

    /**
     * Two supplies with records over the same days: each series must carry its own supply's energy
     * and its own savings, which an assertion over a single supply could not show.
     */
    @Test
    void testDailySavingsAreScopedToTheSupplyRequested() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply otherSupply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(SupplyConsumptionSavingsInfluxLoader.OTHER_CUPS_WITH_SAVINGS).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + otherSupply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", SAVINGS_START_DATE)
                        .queryParam("endDate", "2023-04-11T23:59:59+02:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].cups").value(SupplyConsumptionSavingsInfluxLoader.OTHER_CUPS_WITH_SAVINGS))
                // 4.00 kWh worth 0.60, then 2.00 kWh worth 0.30.
                .andExpect(jsonPath("$[0].savingsEur").value(closeTo(0.60, TOLERANCE)))
                .andExpect(jsonPath("$[1].savingsEur").value(closeTo(0.30, TOLERANCE)));
    }

    /**
     * Generated energy used to come back as a constant 0.0 on this series: the grouped query summed
     * every energy field except `generation_energy_kwh`, so the mapper only ever saw a null. The
     * daily totals here -- 12.00, 2.00, 0.50, nothing and 4.00 kWh -- differ from the
     * self-consumption totals of the same days on purpose, so this cannot pass by reading the wrong
     * column.
     */
    @Test
    void testDailyConsumptionSumsGeneratedEnergy() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(SupplyConsumptionSavingsInfluxLoader.CUPS_WITH_SAVINGS).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/daily")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", SAVINGS_START_DATE)
                        .queryParam("endDate", SAVINGS_END_DATE))
                .andExpect(status().isOk())
                // 2.00 + 4.00 + 6.00 on the 10th.
                .andExpect(jsonPath("$[0].generationEnergyKWh").value(closeTo(12.00, TOLERANCE)))
                .andExpect(jsonPath("$[1].generationEnergyKWh").value(closeTo(2.00, TOLERANCE)))
                .andExpect(jsonPath("$[2].generationEnergyKWh").value(closeTo(0.50, TOLERANCE)))
                // The 13th has no record at all, so it stays at zero like its other energy fields.
                .andExpect(jsonPath("$[3].generationEnergyKWh").value(closeTo(0.00, TOLERANCE)))
                .andExpect(jsonPath("$[4].generationEnergyKWh").value(closeTo(4.00, TOLERANCE)));
    }
}
