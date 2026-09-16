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
class GetSupplyMonthlyConsumptionControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";
    private static final String START_DATE = "2023-04-01T00:00:00Z";
    private static final String END_DATE = "2023-04-30T23:59:59Z";
    private static final String CUPS_CODE = "ES0031406912345678JN0F";
    private static final String SAVINGS_START_DATE = "2024-01-01T00:00:00+01:00";
    private static final String SAVINGS_END_DATE = "2024-04-30T23:59:59+02:00";
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
    void testGetSupplyMonthlyConsumptionAsAdmin() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("consumptionKWh")))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].cups").value(CUPS_CODE));
    }

    @Test
    void testGetSupplyMonthlyConsumptionAsOwner() throws Exception {
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

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
                        .header(HttpHeaders.AUTHORIZATION, partnerToken)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("consumptionKWh")))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].cups").value(CUPS_CODE));
    }

    @Test
    void testGetSupplyMonthlyConsumptionAsNonOwner() throws Exception {
        // Create a supply for one user
        User ownerUser = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(ownerUser).withCode(CUPS_CODE).build(),
                UserId.of(ownerUser.getId()));

        // Login as a different partner user
        String partnerToken = loginAsPartner();

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
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
    void testGetSupplyMonthlyConsumptionWithMissingStartDate() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("endDate", END_DATE))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")));
    }

    @Test
    void testGetSupplyMonthlyConsumptionWithMissingEndDate() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
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
    void testGetSupplyMonthlyConsumptionWithUnknownSupply() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        UUID supplyId = UUID.randomUUID();

        mockMvc.perform(get(URL + "/" + supplyId + "/consumption/monthly")
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

        mockMvc.perform(get(URL + "/" + randomId + "/consumption/monthly")
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

        mockMvc.perform(get(URL + "/" + randomId + "/consumption/monthly")
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

        mockMvc.perform(get(URL + "/" + randomId + "/consumption/monthly")
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
    void testMonthlyConsumptionKeepsEveryPreviouslyDeclaredField() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
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
    void testMonthlyConsumptionNoLongerCarriesTheEmptyField() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(CUPS_CODE).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"cups\"")))
                .andExpect(content().string(not(containsString("\"empty\""))));
    }

    /**
     * AC2. Four months with four different self-consumption figures, priced at the estimated
     * 0.15 EUR/kWh: 100.00, 0.50, 0.00 and 8.00 kWh are worth 15.00, 0.075, 0 and 1.20, which
     * present as 15.00, 0.08, 0.00 and 1.20. February lands exactly on a half cent, so it only
     * comes out at 0.08 under HALF_UP.
     */
    @Test
    void testMonthlyConsumptionReportsEstimatedSavingsPerMonth() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(SupplyConsumptionSavingsInfluxLoader.CUPS_WITH_SAVINGS).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", SAVINGS_START_DATE)
                        .queryParam("endDate", SAVINGS_END_DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].date").value("2024/01/01"))
                .andExpect(jsonPath("$[0].selfConsumptionEnergyKWh").value(closeTo(100.00, TOLERANCE)))
                .andExpect(jsonPath("$[0].savingsEur").value(closeTo(15.00, TOLERANCE)))
                .andExpect(jsonPath("$[1].date").value("2024/02/01"))
                .andExpect(jsonPath("$[1].savingsEur").value(closeTo(0.08, TOLERANCE)))
                .andExpect(jsonPath("$[2].date").value("2024/03/01"))
                .andExpect(jsonPath("$[2].savingsEur").value(closeTo(0.00, TOLERANCE)))
                .andExpect(jsonPath("$[3].date").value("2024/04/01"))
                .andExpect(jsonPath("$[3].savingsEur").value(closeTo(1.20, TOLERANCE)))
                .andExpect(jsonPath("$[0].tariffSource").value("ESTIMATE"))
                .andExpect(jsonPath("$[3].tariffSource").value("ESTIMATE"))
                .andExpect(content().string(containsString("\"savingsEur\":15.00")))
                .andExpect(content().string(containsString("\"savingsEur\":0.08")))
                .andExpect(content().string(containsString("\"savingsEur\":0.00")))
                .andExpect(content().string(containsString("\"savingsEur\":1.20")));
    }

    /**
     * A monthly bucket is never trimmed by the bounds: an `endDate` in the middle of February still
     * returns the whole month, so its savings must be the whole month's 0.50 kWh at 0.15, not the
     * fraction of the month the request happens to end in.
     */
    @Test
    void testAMonthSelectedByMidMonthBoundsStillCarriesItsWholeSavings() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(SupplyConsumptionSavingsInfluxLoader.CUPS_WITH_SAVINGS).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + supply.getId() + "/consumption/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", SAVINGS_START_DATE)
                        .queryParam("endDate", "2024-02-15T12:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].date").value("2024/02/01"))
                .andExpect(jsonPath("$[1].selfConsumptionEnergyKWh").value(closeTo(0.50, TOLERANCE)))
                .andExpect(jsonPath("$[1].savingsEur").value(closeTo(0.08, TOLERANCE)))
                .andExpect(content().string(containsString("\"savingsEur\":0.08")));
    }

    /**
     * Two supplies with a pre-aggregate for the same month: the series must carry the requested
     * supply's own energy and savings.
     */
    @Test
    void testMonthlySavingsAreScopedToTheSupplyRequested() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply otherSupply = createSupplyRepository.create(
                SupplyMother.random(user).withCode(SupplyConsumptionSavingsInfluxLoader.OTHER_CUPS_WITH_SAVINGS).build(),
                UserId.of(user.getId()));

        mockMvc.perform(get(URL + "/" + otherSupply.getId() + "/consumption/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", SAVINGS_START_DATE)
                        .queryParam("endDate", SAVINGS_END_DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cups").value(SupplyConsumptionSavingsInfluxLoader.OTHER_CUPS_WITH_SAVINGS))
                // 20.00 kWh worth 3.00.
                .andExpect(jsonPath("$[0].savingsEur").value(closeTo(3.00, TOLERANCE)))
                .andExpect(content().string(containsString("\"savingsEur\":3.00")));
    }
}
