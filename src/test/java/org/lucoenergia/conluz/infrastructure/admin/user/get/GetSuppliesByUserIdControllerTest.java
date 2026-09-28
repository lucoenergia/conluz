package org.lucoenergia.conluz.infrastructure.admin.user.get;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetSuppliesByUserIdControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;

    @Test
    void testGetSuppliesByUserId_shouldReturnSuppliesWhenAdminRequestsAnyUser() throws Exception {
        // Create a user with supplies
        User user = UserMother.randomUser();
        createUserRepository.create(user);

        Supply supply1 = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES0031300119158001DL0F")
                .withUser(user)
                .withName("Supply 1")
                .withAddress("Address 1")
                .withEnabled(true)
                .build();
        createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES0031300119158001DL0G")
                .withUser(user)
                .withName("Supply 2")
                .withAddress("Address 2")
                .withEnabled(true)
                .build();
        createSupplyRepository.create(supply2, UserId.of(user.getId()));

        // Login as default admin
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void testGetSuppliesByUserId_shouldReturnSuppliesWhenUserRequestsOwnSupplies() throws Exception {
        // Create a user with supplies
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);

        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES0031300119158001DL0H")
                .withUser(user)
                .withName("My Supply")
                .withAddress("My Address")
                .withEnabled(true)
                .build();
        Supply createdSupply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Login as the user
        String loginBody = "{\"username\": \"" + user.getPersonalId() + "\",\"password\": \"" + user.getPassword() + "\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String authHeader = "Bearer " + objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(createdSupply.getId().toString()))
                .andExpect(jsonPath("$[0].code").value(createdSupply.getCode()));
    }

    /**
     * The endpoint is user-scoped, so a single response can span communities. Each supply must carry
     * its own -- a fixture with one community would not tell a correct mapping from a constant one.
     */
    @Test
    void testGetSuppliesByUserId_eachSupplyReportsItsOwnCommunity() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());

        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);

        Supply supplyInA = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), communityA.getId());
        Supply supplyInB = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), communityB.getId());

        String authHeader = loginUser(user);

        MvcResult result = mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        Map<String, String> communityIdBySupplyId = new HashMap<>();
        Map<String, String> communityNameBySupplyId = new HashMap<>();
        for (JsonNode supply : objectMapper.readTree(result.getResponse().getContentAsString())) {
            communityIdBySupplyId.put(supply.get("id").asText(), supply.get("community").get("id").asText());
            communityNameBySupplyId.put(supply.get("id").asText(), supply.get("community").get("name").asText());
        }

        assertEquals(2, communityIdBySupplyId.size());
        assertEquals(communityA.getId().toString(), communityIdBySupplyId.get(supplyInA.getId().toString()));
        assertEquals(communityB.getId().toString(), communityIdBySupplyId.get(supplyInB.getId().toString()));
        assertEquals(communityA.getName(), communityNameBySupplyId.get(supplyInA.getId().toString()));
        assertEquals(communityB.getName(), communityNameBySupplyId.get(supplyInB.getId().toString()));
    }

    @Test
    void testGetSuppliesByUserId_shouldReturnForbiddenWhenNonAdminRequestsOtherUserSupplies() throws Exception {
        // Create two users
        User user1 = UserMother.randomUser();
        user1.enable();
        createUserRepository.create(user1);

        User user2 = UserMother.randomUser();
        user2.enable();
        createUserRepository.create(user2);

        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES0031300119158001DL0I")
                .withUser(user2)
                .withName("User 2 Supply")
                .withAddress("User 2 Address")
                .withEnabled(true)
                .build();
        createSupplyRepository.create(supply, UserId.of(user2.getId()));

        // Login as user1 and try to access user2's supplies
        String loginBody = "{\"username\": \"" + user1.getPersonalId() + "\",\"password\": \"" + user1.getPassword() + "\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String authHeader = "Bearer " + objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", user2.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void testGetSuppliesByUserId_shouldReturnUnauthorizedWhenNoToken() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/" + userId + "/supplies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetSuppliesByUserId_shouldReturnEmptyListWhenUserHasNoSupplies() throws Exception {
        // Create a user with no supplies
        User user = UserMother.randomUser();
        createUserRepository.create(user);

        // Login as default admin
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
