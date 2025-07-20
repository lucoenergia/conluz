package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SetSupplyTariffService;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffRequest;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SetSupplyTariffControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies";

    @Autowired
    private CreateUserRepository createUserRepository;
    
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    
    @Autowired
    private SetSupplyTariffService setSupplyTariffService;

    @Test
    void testSetSupplyTariff() throws Exception {
        // Login as admin
        String authHeader = loginAsDefaultAdmin();

        // Create a user
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        // Create a supply
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Create request body
        SupplyTariffRequest request = new SupplyTariffRequest();
        request.setValley(0.12);
        request.setPeak(0.18);
        request.setOffPeak(0.14);

        String bodyAsString = objectMapper.writeValueAsString(request);

        // Test setting tariff
        mockMvc.perform(put(String.format("%s/%s/tariffs", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valley").value(request.getValley()))
                .andExpect(jsonPath("$.peak").value(request.getPeak()))
                .andExpect(jsonPath("$.offPeak").value(request.getOffPeak()))
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()));
    }

    @Test
    void testSetSupplyTariffWithUnknownSupply() throws Exception {
        // Login as admin
        String authHeader = loginAsDefaultAdmin();

        UUID nonExistentId = UUID.randomUUID();

        // Create request body
        SupplyTariffRequest request = new SupplyTariffRequest();
        request.setValley(0.12);
        request.setPeak(0.18);
        request.setOffPeak(0.14);

        String bodyAsString = objectMapper.writeValueAsString(request);

        // Test setting tariff for non-existent supply
        mockMvc.perform(put(String.format("%s/%s/tariffs", PATH, nonExistentId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testSetSupplyTariffWithMissingRequiredFields(String body) throws Exception {
        // Login as admin
        String authHeader = loginAsDefaultAdmin();

        // Create a user
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        // Create a supply
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Test setting tariff with missing required fields
        mockMvc.perform(put(String.format("%s/%s/tariffs", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithMissingRequiredFields() {
        return List.of(
                """
                {
                  "peak": 0.18,
                  "offPeak": 0.14
                }
                """,
                """
                {
                  "valley": 0.12,
                  "offPeak": 0.14
                }
                """,
                """
                {
                  "valley": 0.12,
                  "peak": 0.18
                }
                """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidValues")
    void testSetSupplyTariffWithInvalidValues(String body) throws Exception {
        // Login as admin
        String authHeader = loginAsDefaultAdmin();

        // Create a user
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        // Create a supply
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Test setting tariff with invalid values
        mockMvc.perform(put(String.format("%s/%s/tariffs", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithInvalidValues() {
        return List.of(
                """
                {
                  "valley": -0.12,
                  "peak": 0.18,
                  "offPeak": 0.14
                }
                """,
                """
                {
                  "valley": 0.12,
                  "peak": -0.18,
                  "offPeak": 0.14
                }
                """,
                """
                {
                  "valley": 0.12,
                  "peak": 0.18,
                  "offPeak": -0.14
                }
                """);
    }

    @Test
    void testWithoutToken() throws Exception {
        // Create a user
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        // Create a supply
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Create request body
        SupplyTariffRequest request = new SupplyTariffRequest();
        request.setValley(0.12);
        request.setPeak(0.18);
        request.setOffPeak(0.14);

        String bodyAsString = objectMapper.writeValueAsString(request);

        // Test setting tariff without token
        mockMvc.perform(put(String.format("%s/%s/tariffs", PATH, supply.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        // Test getting tariff without token
        mockMvc.perform(get(String.format("%s/%s/tariffs", PATH, supply.getId())))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAsSupplyOwner() throws Exception {
        // Create a partner user (non-admin)
        User partnerUser = UserMother.randomUser();
        partnerUser.setRole(Role.PARTNER);
        partnerUser.enable();
        final String partnerUserPassword = partnerUser.getPassword();
        partnerUser = createUserRepository.create(partnerUser);
        
        // Login as the partner user
        String loginBody = "{\"username\": \"" + partnerUser.getPersonalId() + "\",\"password\": \"" + partnerUserPassword + "\"}";
        String partnerAuthHeader = "Bearer " + objectMapper.readTree(
                mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .get("token").asText();

        // Create a supply owned by the partner
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(partnerUser.getId()));

        // Create request body
        SupplyTariffRequest request = new SupplyTariffRequest();
        request.setValley(0.12);
        request.setPeak(0.18);
        request.setOffPeak(0.14);

        String bodyAsString = objectMapper.writeValueAsString(request);

        // Test setting tariff as supply owner
        mockMvc.perform(put(String.format("%s/%s/tariffs", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, partnerAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valley").value(request.getValley()))
                .andExpect(jsonPath("$.peak").value(request.getPeak()))
                .andExpect(jsonPath("$.offPeak").value(request.getOffPeak()))
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()));
    }

    @Test
    void testAsNonOwner() throws Exception {
        // Create a partner user (non-admin)
        User partnerUser = UserMother.randomUser();
        partnerUser.setRole(Role.PARTNER);
        partnerUser.enable();
        final String partnerUserPassword = partnerUser.getPassword();
        partnerUser = createUserRepository.create(partnerUser);
        
        // Login as the partner user
        String loginBody = "{\"username\": \"" + partnerUser.getPersonalId() + "\",\"password\": \"" + partnerUserPassword + "\"}";
        String partnerAuthHeader = "Bearer " + objectMapper.readTree(
                mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .get("token").asText();

        // Create another user
        User otherUser = UserMother.randomUser();
        otherUser = createUserRepository.create(otherUser);

        // Create a supply owned by the other user
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(otherUser.getId()));

        // Create request body
        SupplyTariffRequest request = new SupplyTariffRequest();
        request.setValley(0.12);
        request.setPeak(0.18);
        request.setOffPeak(0.14);

        String bodyAsString = objectMapper.writeValueAsString(request);

        // Test setting tariff as non-owner (should fail with 403 Forbidden)
        mockMvc.perform(put(String.format("%s/%s/tariffs", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, partnerAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}