package org.lucoenergia.conluz.infrastructure.admin.user.get;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
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
    private CreateMembershipService createMembershipService;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;

    @Test
    void testGetSuppliesByUserId_shouldReturnSuppliesWhenCommunityAdminRequestsAMemberOfTheirCommunity()
            throws Exception {
        // Create a user with supplies, in the community the caller administers
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);

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

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

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
    void testGetSuppliesByUserId_shouldReturnNotFoundWhenNonAdminRequestsOtherUserSupplies() throws Exception {
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
        // Create a user with no supplies, and let them ask about themselves
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);

        String authHeader = loginUser(user);

        mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
    @Test
    void testGetSuppliesByUserId_shouldForbidAPlatformAdminWhoAdministersNoneOfTheUserCommunities()
            throws Exception {
        // The supply route and the user route must agree. A platform admin gets 404 asking for any
        // one of these supplies directly, so listing them through their owner cannot be a way round
        // it. They can see the user, so the denial is a 403 and leaks nothing.
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);

        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES0031300119158001DL0H")
                .withUser(user)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
        createSupplyRepository.create(supply, UserId.of(user.getId()));

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden());

        // ... and the same caller is told the supply itself does not exist.
        mockMvc.perform(get(String.format("/api/v1/supplies/%s", supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // --- #326: the listing carries only what the caller may read one by one ---

    @Test
    void testGetSuppliesByUserId_anAdminOfOneCommunityReceivesOnlyThatCommunitysSupplies() throws Exception {
        TwoCommunityMember target = twoCommunityMember();
        String adminOfA = loginAsCommunityAdmin(target.communityA.getId());

        // Both halves, because the point is that the listing and the single read agree: every row the
        // listing returns is readable one by one, and every row it omits is a 404 one by one.
        assertEquals(Set.of(target.supplyInA.getId().toString()), listedSupplyIds(target.user, adminOfA));
        assertSupplyStatus(target.supplyInA, adminOfA, HttpStatus.OK);
        assertSupplyStatus(target.supplyInB, adminOfA, HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetSuppliesByUserId_anAdminOfBothCommunitiesReceivesEverySupply() throws Exception {
        TwoCommunityMember target = twoCommunityMember();
        User admin = UserMother.randomUser();
        admin.enable();
        createUserRepository.create(admin);
        createMembershipService.create(target.communityA.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        createMembershipService.create(target.communityB.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);

        assertEquals(Set.of(target.supplyInA.getId().toString(), target.supplyInB.getId().toString()),
                listedSupplyIds(target.user, loginUser(admin)));
    }

    @Test
    void testGetSuppliesByUserId_theUserThemselvesReceivesEverySupplyAcrossCommunities() throws Exception {
        TwoCommunityMember target = twoCommunityMember();

        assertEquals(Set.of(target.supplyInA.getId().toString(), target.supplyInB.getId().toString()),
                listedSupplyIds(target.user, loginUser(target.user)));
    }

    @Test
    void testGetSuppliesByUserId_aPlainMemberOfACommunityOfTheUserIsStillNotFound() throws Exception {
        TwoCommunityMember target = twoCommunityMember();

        mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", target.user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityMember(target.communityA.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private record TwoCommunityMember(User user, Community communityA, Community communityB,
                                      Supply supplyInA, Supply supplyInB) {
    }

    private TwoCommunityMember twoCommunityMember() {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipService.create(communityA.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        createMembershipService.create(communityB.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply supplyInA = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), communityA.getId());
        Supply supplyInB = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), communityB.getId());
        return new TwoCommunityMember(user, communityA, communityB, supplyInA, supplyInB);
    }

    private Set<String> listedSupplyIds(User target, String authHeader) throws Exception {
        MvcResult result = mockMvc.perform(get(String.format("/api/v1/users/%s/supplies", target.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        Set<String> ids = new HashSet<>();
        for (JsonNode supply : objectMapper.readTree(result.getResponse().getContentAsString())) {
            ids.add(supply.get("id").asText());
        }
        return ids;
    }

    private void assertSupplyStatus(Supply supply, String authHeader, HttpStatus expected) throws Exception {
        mockMvc.perform(get(String.format("/api/v1/supplies/%s", supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expected.value()));
    }
}
