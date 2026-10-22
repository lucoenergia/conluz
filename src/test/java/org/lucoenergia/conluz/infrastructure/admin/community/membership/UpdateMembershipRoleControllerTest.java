package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisterMemberService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the gap in this endpoint's error mapping: a community and a user can both exist while the
 * membership joining them does not, and that case used to escape as a bare {@code RuntimeException}
 * and surface as a 500 with no {@code RestError} body. The community-missing and user-missing cases
 * were already mapped, which is what made this one easy to miss.
 */
@Transactional
class UpdateMembershipRoleControllerTest extends BaseControllerTest {

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private RegisterMemberService registerMemberService;
    @Autowired
    private GetMembershipsRepository getMembershipsRepository;

    @Test
    void patchingAMembershipThatDoesNotExistIsReportedAsNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        // Exists, and is deliberately not a member of this community.
        User outsider = persistUser();
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}",
                        community.getId(), outsider.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"COMMUNITY_ADMIN\"}"))
                .andExpect(status().isNotFound())
                // The RestError body, not an empty 500.
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    /**
     * The happy path, so the test above is known to be failing on the missing membership rather
     * than on the request being malformed or the caller being rejected.
     */
    @Test
    void patchingAnExistingMembershipUpdatesItsRole() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistUser();
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}",
                        community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"COMMUNITY_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMMUNITY_ADMIN"));
    }

    @Test
    @DisplayName("MEM-024 giving the admin role to a membership whose record has no contact email is refused with "
            + "400, and the role is unchanged")
    void promotingAMembershipWithoutAContactEmail_isRefused() throws Exception {
        CommunityEntity community = persistCommunity();
        UUID memberId = registeredMember(community.getId(), null);
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}",
                        community.getId(), memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"COMMUNITY_ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_WITHOUT_CONTACT_EMAIL"));

        assertEquals(CommunityRole.COMMUNITY_MEMBER, getMembershipsRepository
                .findByUserIdAndCommunityId(memberId, community.getId()).orElseThrow().getRole());
    }

    @Test
    @DisplayName("MEM-031 the platform admin who is not an admin of the community is refused changing a role")
    void promotingAMembership_isRefusedToThePlatformAdmin() throws Exception {
        CommunityEntity community = persistCommunity();
        UUID memberId = registeredMember(community.getId(), null);
        String platformAdminToken = loginAsDefaultPlatformAdmin();

        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}",
                        community.getId(), memberId)
                        .header(HttpHeaders.AUTHORIZATION, platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"COMMUNITY_ADMIN\"}"))
                .andExpect(status().isForbidden());

        assertEquals(CommunityRole.COMMUNITY_MEMBER, getMembershipsRepository
                .findByUserIdAndCommunityId(memberId, community.getId()).orElseThrow().getRole());
    }

    @Test
    @DisplayName("MEM-024 giving the admin role to a membership whose record has a contact email succeeds")
    void promotingAMembershipWithAContactEmail_succeeds() throws Exception {
        CommunityEntity community = persistCommunity();
        UUID memberId = registeredMember(community.getId(), "contact@example.org");
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}",
                        community.getId(), memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"COMMUNITY_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMMUNITY_ADMIN"));

        assertEquals(CommunityRole.COMMUNITY_ADMIN, getMembershipsRepository
                .findByUserIdAndCommunityId(memberId, community.getId()).orElseThrow().getRole());
    }

    /**
     * The 404 the fix produces has to be in the contract too, or a generated client treats it as an
     * undeclared failure.
     */
    @Test
    void theContractDeclaresNotFoundForThisOperation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responses = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("paths")
                .path("/api/v1/communities/{communityId}/memberships/{userId}")
                .path("patch")
                .path("responses");

        assertTrue(!responses.path("404").isMissingNode(),
                () -> "the PATCH operation declares no 404: " + fieldNames(responses));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    /**
     * A member registered the way a community admin registers one, with the given contact email on their record.
     */
    private UUID registeredMember(UUID communityId, String contactEmail) {
        User registrar = persistUser();
        return registerMemberService.register(communityId, UserId.of(registrar.getId()),
                        UserPersonalId.of(UserMother.randomPersonalId()),
                        new MemberRecord("Lucía Pérez", null, contactEmail, null, null))
                .membership().getUser().getId();
    }

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }
}
