package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetMembershipsControllerTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;

    @Test
    void testGetMembershipsReturnsEnrichedResponse() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User admin = createUser("Alice Admin", "alice@test.com");
        User member = createUser("Bob Member", "bob@test.com");

        createMembership(admin, community, CommunityRole.COMMUNITY_ADMIN);
        createMembership(member, community, CommunityRole.COMMUNITY_MEMBER);

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/communities/{id}/memberships", community.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.fullName == 'Alice Admin')].email").value("alice@test.com"))
                .andExpect(jsonPath("$[?(@.fullName == 'Alice Admin')].role").value("COMMUNITY_ADMIN"))
                .andExpect(jsonPath("$[?(@.fullName == 'Bob Member')].email").value("bob@test.com"))
                .andExpect(jsonPath("$[?(@.fullName == 'Bob Member')].role").value("COMMUNITY_MEMBER"))
                .andExpect(jsonPath("$[0].userId").isString())
                .andExpect(jsonPath("$[0].communityId").value(community.getId().toString()))
                .andExpect(jsonPath("$[0].enabled").isBoolean());
    }

    @Test
    void testGetMembershipsWithSingleMember() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User member = createUser("Charlie", "charlie@test.com");

        createMembership(member, community, CommunityRole.COMMUNITY_MEMBER);

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/communities/{id}/memberships", community.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Charlie"))
                .andExpect(jsonPath("$[0].email").value("charlie@test.com"))
                .andExpect(jsonPath("$[0].role").value("COMMUNITY_MEMBER"))
                .andExpect(jsonPath("$[0].userId").isString())
                .andExpect(jsonPath("$[0].communityId").value(community.getId().toString()))
                .andExpect(jsonPath("$[0].enabled").isBoolean());
    }

    private User createUser(String fullName, String email) {
        User user = UserMother.randomUser();
        user.setFullName(fullName);
        user.setEmail(email);
        createUserRepository.create(user);
        return user;
    }

    private void createMembership(User user, Community community, CommunityRole role) {
        UserEntity userEntity = userRepository.findByPersonalId(user.getPersonalId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getPersonalId()));
        CommunityEntity communityEntity = communityJpaRepository.findById(community.getId())
                .orElseThrow(() -> new IllegalStateException("Community not found: " + community.getId()));

        CommunityMembershipEntity membership = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withUser(userEntity)
                .withCommunity(communityEntity)
                .withRole(role)
                .withEnabled(true)
                .build();
        communityMembershipJpaRepository.save(membership);
    }
}
