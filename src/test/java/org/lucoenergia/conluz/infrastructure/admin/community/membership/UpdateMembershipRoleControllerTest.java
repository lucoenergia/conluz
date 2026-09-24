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
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
    private CreateMembershipService createMembershipService;

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
        createMembershipService.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}",
                        community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"COMMUNITY_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMMUNITY_ADMIN"));
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

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }
}
