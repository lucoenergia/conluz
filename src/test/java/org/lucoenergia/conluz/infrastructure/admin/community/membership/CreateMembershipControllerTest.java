package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adding a user who is already a member used to reach the {@code (user_id, community_id)} unique
 * constraint, whose {@code DataIntegrityViolationException} nothing maps, and surface as a 500 with
 * no {@code RestError} body.
 *
 * <p>It is now a 409: the body is well-formed and the caller is authorized, and what stops the
 * request is the state of the resource. That is the mapping the authorization policy prescribes for
 * a state conflict, and it says such a case must never be a 400.
 */
@Transactional
class CreateMembershipControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships";

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void addingAUserWhoIsAlreadyAMemberIsReportedAsAConflict() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(post(PATH, community.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(member.getId(), "COMMUNITY_MEMBER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists())
                // The typed code and its params, so a client can react without parsing the message.
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.errors[0].params.userId").value(member.getId().toString()))
                .andExpect(jsonPath("$.errors[0].params.communityId").value(community.getId().toString()));
    }

    /**
     * A different role on the duplicate is still a duplicate: the conflict is the membership, not
     * the role it would carry. Changing a role is the PATCH endpoint's job.
     */
    @Test
    void addingAnExistingMemberWithADifferentRoleIsStillAConflictAndChangesNothing() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(post(PATH, community.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(member.getId(), "COMMUNITY_ADMIN")))
                .andExpect(status().isConflict());

        assertEquals(CommunityRole.COMMUNITY_MEMBER,
                membershipJpaRepository.findByUserIdAndCommunityId(member.getId(), community.getId())
                        .orElseThrow().getRole(),
                "the existing membership's role must not have been overwritten");
    }

    /**
     * The same user in two communities is not a duplicate: the uniqueness is per pair, and this is
     * the case a check on the user alone would wrongly reject.
     */
    @Test
    void addingTheSameUserToAnotherCommunityIsAllowed() throws Exception {
        CommunityEntity community = persistCommunity();
        CommunityEntity otherCommunity = persistCommunity();
        User member = persistMember(community.getId());
        String otherAdminToken = loginAsCommunityAdmin(otherCommunity.getId());

        mockMvc.perform(post(PATH, otherCommunity.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(member.getId(), "COMMUNITY_MEMBER")))
                .andExpect(status().isOk());

        assertTrue(membershipJpaRepository.findByUserIdAndCommunityId(member.getId(), community.getId()).isPresent());
        assertTrue(membershipJpaRepository.findByUserIdAndCommunityId(member.getId(), otherCommunity.getId()).isPresent());
    }

    /**
     * The happy path, so the conflict tests are known to be failing on the duplicate rather than on
     * the request being malformed or the caller being rejected.
     */
    @Test
    void addingANewMemberSucceeds() throws Exception {
        CommunityEntity community = persistCommunity();
        User outsider = persistUser();
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(post(PATH, community.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(outsider.getId(), "COMMUNITY_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMMUNITY_MEMBER"));

        assertTrue(membershipJpaRepository.findByUserIdAndCommunityId(outsider.getId(), community.getId()).isPresent());
    }

    /**
     * The 409 has to be in the contract, or a generated client treats it as an undeclared failure
     * and falls into whatever its default error branch is.
     */
    @Test
    void theContractDeclaresTheConflictForThisOperation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responses = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("paths")
                .path("/api/v1/communities/{communityId}/memberships")
                .path("post")
                .path("responses");

        assertTrue(!responses.path("409").isMissingNode(),
                () -> "the POST operation declares no 409: " + fieldNames(responses));
    }

    private static String body(UUID userId, String role) {
        return "{\"userId\": \"" + userId + "\", \"role\": \"" + role + "\"}";
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User persistMember(UUID communityId) {
        User member = persistUser();
        createMembershipService.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }
}
