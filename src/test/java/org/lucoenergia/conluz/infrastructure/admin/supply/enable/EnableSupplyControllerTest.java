package org.lucoenergia.conluz.infrastructure.admin.supply.enable;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class EnableSupplyControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private GetSupplyRepository getSupplyRepository;

    @Test
    void testEnableSupply_success() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random(user).build();
        // ensure it is disabled initially
        supply = new Supply.Builder()
                .withId(supply.getId())
                .withCode(supply.getCode())
                .withUser(supply.getUser())
                .withName(supply.getName())
                .withAddress(supply.getAddress())
                .withPartitionCoefficient(supply.getPartitionCoefficient())
                .withEnabled(false)
                .withValidDateFrom(supply.getValidDateFrom())
                .withDistributor(supply.getDistributor())
                .withDistributorCode(supply.getDistributorCode())
                .withPointType(supply.getPointType())
                .withThirdParty(supply.isThirdParty())
                .withShellyId(supply.getShellyId())
                .withShellyMac(supply.getShellyMac())
                .withShellyMqttPrefix(supply.getShellyMqttPrefix())
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        mockMvc.perform(post(String.format("%s/%s/enable", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.enabled").value(true));

        // Verify persisted state
        assertThat(getSupplyRepository.findById(SupplyId.of(supply.getId())).get().getEnabled()).isTrue();
    }

    @Test
    void testEnableSupply_idempotent_whenAlreadyEnabled() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random(user).build();
        // ensure it is enabled initially
        supply = new Supply.Builder()
                .withId(supply.getId())
                .withCode(supply.getCode())
                .withUser(supply.getUser())
                .withName(supply.getName())
                .withAddress(supply.getAddress())
                .withPartitionCoefficient(supply.getPartitionCoefficient())
                .withEnabled(true)
                .withValidDateFrom(supply.getValidDateFrom())
                .withDistributor(supply.getDistributor())
                .withDistributorCode(supply.getDistributorCode())
                .withPointType(supply.getPointType())
                .withThirdParty(supply.isThirdParty())
                .withShellyId(supply.getShellyId())
                .withShellyMac(supply.getShellyMac())
                .withShellyMqttPrefix(supply.getShellyMqttPrefix())
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        mockMvc.perform(post(String.format("%s/%s/enable", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(getSupplyRepository.findById(SupplyId.of(supply.getId())).get().getEnabled()).isTrue();
    }

    @Test
    void testEnableSupply_notFound() throws Exception {
        String authHeader = loginAsDefaultAdmin();
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(post(String.format("%s/%s/enable", PATH, randomId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testEnableSupply_withoutToken() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(post(String.format("%s/%s/enable", PATH, randomId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testEnableSupply_withWrongToken() throws Exception {
        UUID randomId = UUID.randomUUID();
        final String wrongToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX + "wrong";

        mockMvc.perform(post(String.format("%s/%s/enable", PATH, randomId))
                        .header(HttpHeaders.AUTHORIZATION, wrongToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testEnableSupply_authenticatedUserWithoutAdminRoleCannotAccess() throws Exception {
        String partnerAuth = loginAsPartner();
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(post(String.format("%s/%s/enable", PATH, randomId))
                        .header(HttpHeaders.AUTHORIZATION, partnerAuth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
