package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.junit.jupiter.api.DisplayName;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetSupplyByIdControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;

    @Test
    void testGetSupplyByIdSuccess() throws Exception {
        // Create a user and a supply
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random(user).build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL + "/" + supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.code").value(supply.getCode()))
                .andExpect(jsonPath("$.enabled").value(supply.getEnabled()));
    }

    @Test
    void testGetSupplyByIdReportsTheOwningCommunity() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());

        String authHeader = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(get(URL + "/" + supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.community.id").value(community.getId().toString()))
                .andExpect(jsonPath("$.community.name").value(community.getName()));
    }

    @Test
    @DisplayName("SUP-003 the owner reads their supply while their membership in its community is active")
    void theOwnerReadsTheirSupplyWhileTheirMembershipIsActive() throws Exception {
        User owner = createOwnerMemberOfDefaultCommunity();
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL + "/" + supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supply.getId().toString()));
    }

    @Test
    @DisplayName("SUP-003 the owner whose membership is disabled is answered not found")
    void theOwnerWhoseMembershipIsDisabledIsAnsweredNotFound() throws Exception {
        User owner = createOwnerMemberOfDefaultCommunity();
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), DEFAULT_COMMUNITY_ID);
        MembershipStateTestSupport.markDisabled(communityMembershipJpaRepository, owner.getId(), DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL + "/" + supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SUP-003 the owner whose membership is pending is answered not found")
    void theOwnerWhoseMembershipIsPendingIsAnsweredNotFound() throws Exception {
        User owner = createOwnerMemberOfDefaultCommunity();
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), DEFAULT_COMMUNITY_ID);
        MembershipStateTestSupport.markPending(communityMembershipJpaRepository, owner.getId(), DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL + "/" + supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetSupplyByIdNotFound() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get(URL + "/" + randomId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithMissingToken() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get(URL + "/" + randomId))
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

        mockMvc.perform(get(URL + "/" + randomId)
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

        mockMvc.perform(get(URL + "/" + randomId)
                        .header(HttpHeaders.AUTHORIZATION, expiredToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private User createOwnerMemberOfDefaultCommunity() {
        User owner = UserMother.randomUser();
        owner.enable();
        createUserRepository.create(owner);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        return owner;
    }
}
