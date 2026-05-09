package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RegisterPartitionCoefficientControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyService createSupplyService;

    @Test
    void registersNewCoefficientAndReturnsWarning() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        String body = """
                {"coefficient": 50.000000, "effectiveAt": "2025-06-01T00:00:00Z"}
                """;

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplyId").value(supply.getId().toString()))
                .andExpect(jsonPath("$.coefficient").isNotEmpty())
                .andExpect(jsonPath("$.validFrom").value("2025-06-01T00:00:00Z"));
    }

    @Test
    void returnsBadRequestForNegativeCoefficient() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        String body = """
                {"coefficient": -1.0, "effectiveAt": "2025-06-01T00:00:00Z"}
                """;

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenCoefficientMissing() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveAt\": \"2025-06-01T00:00:00Z\"}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/supplies/" + UUID_PLACEHOLDER + "/partition-coefficients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForNonAdminRole() throws Exception {
        String authHeader = loginAsPartner();
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coefficient\": 10.0, \"effectiveAt\": \"2025-06-01T00:00:00Z\"}"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    private static final String UUID_PLACEHOLDER = "00000000-0000-0000-0000-000000000000";

    private Supply createTestSupply() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random(user).build();
        return createSupplyService.create(supply, UserId.of(user.getId()));
    }
}
