package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.random;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetCurrentUserControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users/current";

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;

    @Test
    void testGetCurrentUserSuccess() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalId").value(DefaultUserAdminMother.PERSONAL_ID))
                .andExpect(jsonPath("$.fullName").value(DefaultUserAdminMother.FULL_NAME))
                .andExpect(jsonPath("$.email").value(DefaultUserAdminMother.EMAIL))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.isPlatformAdmin").value(true))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.memberships").isEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("ACC-003 a user without email reads their email as null")
    void aUserWithoutEmail_readsTheirEmailAsNull() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        user.setEmail(null);
        createUserRepository.create(user);
        String authHeader = loginUser(user);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalId").value(user.getPersonalId()))
                .andExpect(jsonPath("$.email").hasJsonPath())
                .andExpect(jsonPath("$.email").value(nullValue()));
    }

    @Test
    void testGetCurrentUserReturnsMembershipsForCommunityAdmin() throws Exception {
        loginAsDefaultPlatformAdmin();

        Community community = createCommunityRepository.create(random().build());

        User communityAdmin = UserMother.randomUser();
        communityAdmin.enable();
        createUserRepository.create(communityAdmin);
        createMembershipRepository.create(community.getId(), communityAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);

        String authHeader = loginUser(communityAdmin);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPlatformAdmin").value(false))
                .andExpect(jsonPath("$.memberships").isNotEmpty())
                .andExpect(jsonPath("$.memberships.['" + community.getId().toString() + "']").value(CommunityRole.COMMUNITY_ADMIN.name()));
    }

    @Test
    @DisplayName("MEM-006 the current user's memberships list only the active ones")
    void theCurrentUsersMembershipsLeaveOutPendingAndDisabledOnes() throws Exception {
        Community active = createCommunityRepository.create(random().build());
        Community pending = createCommunityRepository.create(random().build());
        Community disabled = createCommunityRepository.create(random().build());
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipRepository.create(active.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        createMembershipRepository.create(pending.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);
        createMembershipRepository.create(disabled.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);
        MembershipStateTestSupport.markPending(communityMembershipJpaRepository, user.getId(), pending.getId());
        MembershipStateTestSupport.markDisabled(communityMembershipJpaRepository, user.getId(), disabled.getId());

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(user)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships.length()").value(1))
                .andExpect(jsonPath("$.memberships.['" + active.getId() + "']").value(CommunityRole.COMMUNITY_MEMBER.name()))
                .andExpect(jsonPath("$.memberships.['" + pending.getId() + "']").doesNotExist())
                .andExpect(jsonPath("$.memberships.['" + disabled.getId() + "']").doesNotExist());
    }

    @Test
    void testGetCurrentUserUnauthorizedWhenMissingToken() throws Exception {
        mockMvc.perform(get(URL))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
