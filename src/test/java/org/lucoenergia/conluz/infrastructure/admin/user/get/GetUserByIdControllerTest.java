package org.lucoenergia.conluz.infrastructure.admin.user.get;

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
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetUserByIdControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;

    @Test
    void testGetUserById_shouldReturnMemberships() throws Exception {

        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User user = UserMother.randomUser();
        createUserRepository.create(user);
        createMembership(user, community, CommunityRole.COMMUNITY_ADMIN);

        String authHeader = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(get(String.format("/api/v1/users/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.memberships['" + community.getId() + "']")
                        .value(CommunityRole.COMMUNITY_ADMIN.name()));
    }

    @Test
    void testGetUserById_shouldReturnUser() throws Exception {

        // Create a member of a community, read by its admin
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);

        String authHeader = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(get(String.format("/api/v1/users/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.personalId").value(user.getPersonalId()))
                .andExpect(jsonPath("$.number").value(user.getNumber()))
                .andExpect(jsonPath("$.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.address").value(user.getAddress()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.phoneNumber").value(user.getPhoneNumber()))
                .andExpect(jsonPath("$.enabled").value(user.isEnabled()));
    }

    @Test
    void testGetUserById_shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetUserById_shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/users/invalid-uuid")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetUserById_shouldReturnUnauthorizedWhenNoToken() throws Exception {

        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetUserById_shouldReturnForbiddenWhenUserIsNotAdmin() throws Exception {

        User user = UserMother.randomUser();
        createUserRepository.create(user);

        String authHeader = loginAsPartner();

        mockMvc.perform(get(String.format("/api/v1/users/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    // --- #336: memberships are limited to what the caller could read directly ---

    @Test
    void testGetUserById_anAdminOfOneCommunitySeesOnlyThatCommunitysMembership() throws Exception {
        TwoCommunityMember target = twoCommunityMember();
        String adminOfA = loginAsCommunityAdmin(target.communityA.getId());

        mockMvc.perform(get(String.format("/api/v1/users/%s", target.user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships.length()").value(1))
                .andExpect(jsonPath("$.memberships['" + target.communityA.getId() + "']")
                        .value(CommunityRole.COMMUNITY_MEMBER.name()))
                // Decided on the full memberships, before the response narrowed them.
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canListSupplies").value(true))
                // Administering one of them is never enough to change the account (#415).
                .andExpect(jsonPath("$.capabilities.canEdit").value(false));
    }

    @Test
    @DisplayName("ACC-020 a platform admin who administers none of the user's communities is answered 404")
    void testGetUserById_aPlatformAdminIsToldTheUserDoesNotExist() throws Exception {
        TwoCommunityMember target = twoCommunityMember();

        mockMvc.perform(get(String.format("/api/v1/users/%s", target.user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginAsDefaultPlatformAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetUserById_aUserReadingThemselvesSeesEveryOneOfTheirMemberships() throws Exception {
        TwoCommunityMember target = twoCommunityMember();

        mockMvc.perform(get(String.format("/api/v1/users/%s", target.user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginUser(target.user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships.length()").value(2));
    }

    private record TwoCommunityMember(User user, Community communityA, Community communityB) {
    }

    private TwoCommunityMember twoCommunityMember() {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipRepository.create(communityA.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        createMembershipRepository.create(communityB.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);
        return new TwoCommunityMember(user, communityA, communityB);
    }

    private void createMembership(User user, Community community, CommunityRole role) {
        createMembershipRepository.create(community.getId(), user.getId(), role);
    }
}
