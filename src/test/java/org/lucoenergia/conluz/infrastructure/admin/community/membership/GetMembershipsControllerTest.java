package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetMembershipsControllerTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;

    @Test
    void testGetMembershipsReturnsEnrichedResponse() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User admin = createUser("Alice Admin", "alice@test.com");
        User member = createUser("Bob Member", "bob@test.com");

        createMembership(admin, community, CommunityRole.COMMUNITY_ADMIN);
        createMembership(member, community, CommunityRole.COMMUNITY_MEMBER);

        String authHeader = loginUser(admin);

        mockMvc.perform(get("/api/v1/communities/{id}/memberships", community.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.fullName == 'Alice Admin')].contactEmail").value("alice@test.com"))
                .andExpect(jsonPath("$[?(@.fullName == 'Alice Admin')].role").value("COMMUNITY_ADMIN"))
                .andExpect(jsonPath("$[?(@.fullName == 'Bob Member')].contactEmail").value("bob@test.com"))
                .andExpect(jsonPath("$[?(@.fullName == 'Bob Member')].role").value("COMMUNITY_MEMBER"))
                .andExpect(jsonPath("$[0].user.id").isString())
                .andExpect(jsonPath("$[0].communityId").value(community.getId().toString()))
                .andExpect(jsonPath("$[0].enabled").isBoolean());
    }

    @Test
    void testGetMembershipsWithSingleMember() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User member = createUser("Charlie", "charlie@test.com");

        createMembership(member, community, CommunityRole.COMMUNITY_MEMBER);

        // Reading the roster needs a community admin, who is on it as well.
        String authHeader = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(get("/api/v1/communities/{id}/memberships", community.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.fullName == 'Charlie')].contactEmail").value("charlie@test.com"))
                .andExpect(jsonPath("$[?(@.fullName == 'Charlie')].role").value("COMMUNITY_MEMBER"))
                .andExpect(jsonPath("$[0].user.id").isString())
                .andExpect(jsonPath("$[0].communityId").value(community.getId().toString()))
                .andExpect(jsonPath("$[0].enabled").isBoolean());
    }

    @Test
    @DisplayName("MEM-031 a platform admin who is not an admin of the community is refused its roster")
    void aPlatformAdminIsRefusedTheRoster() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        createMembership(createUser("Dana", "dana@test.com"), community, CommunityRole.COMMUNITY_MEMBER);

        mockMvc.perform(get("/api/v1/communities/{id}/memberships", community.getId())
                        .header("Authorization", loginAsDefaultPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEM-005 the roster lists pending and disabled memberships, each state shown on its own")
    void theRosterListsPendingAndDisabledMembershipsWithTheirState() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User active = createUser("Active", "active@test.com");
        User pending = createUser("Pending", "pending@test.com");
        User disabled = createUser("Disabled", "disabled@test.com");
        createMembership(active, community, CommunityRole.COMMUNITY_MEMBER);
        createMembership(pending, community, CommunityRole.COMMUNITY_MEMBER);
        createMembership(disabled, community, CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(communityMembershipJpaRepository, pending.getId(), community.getId());
        MembershipStateTestSupport.markDisabled(communityMembershipJpaRepository, disabled.getId(), community.getId());

        String authHeader = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(get("/api/v1/communities/{id}/memberships", community.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                // the three members plus the calling admin
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.fullName == 'Active')].acceptedAt", contains(notNullValue())))
                .andExpect(jsonPath("$[?(@.fullName == 'Active')].enabled", contains(true)))
                .andExpect(jsonPath("$[?(@.fullName == 'Pending')].acceptedAt", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.fullName == 'Pending')].enabled", contains(true)))
                .andExpect(jsonPath("$[?(@.fullName == 'Disabled')].acceptedAt", contains(notNullValue())))
                .andExpect(jsonPath("$[?(@.fullName == 'Disabled')].enabled", contains(false)));
    }

    private User createUser(String fullName, String email) {
        User user = UserMother.randomUser();
        user.setFullName(fullName);
        user.setEmail(email);
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private void createMembership(User user, Community community, CommunityRole role) {
        createMembershipRepository.create(community.getId(), user.getId(), role);
    }
}
