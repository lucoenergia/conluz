package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetSupplyTariffControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies";

    @Autowired
    private CreateUserRepository createUserRepository;
    
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    
    @Test
    void testGetSupplyTariff() throws Exception {
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
                .andExpect(status().isOk());

        // Test getting tariff
        mockMvc.perform(get(String.format("%s/%s/tariffs", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valley").value(0.12))
                .andExpect(jsonPath("$.peak").value(0.18))
                .andExpect(jsonPath("$.offPeak").value(0.14))
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()));
    }

    @Test
    void testGetNonExistentSupplyTariff() throws Exception {
        // Login as admin
        String authHeader = loginAsDefaultAdmin();

        // Create a user
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        // Create a supply
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Test getting non-existent tariff
        mockMvc.perform(get(String.format("%s/%s/tariffs", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}