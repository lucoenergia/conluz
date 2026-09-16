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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting a membership that does not exist used to answer success after deleting nothing, which
 * tells a caller who named the wrong user or the wrong community that they succeeded. The
 * community-missing and user-missing cases were never reached: the repository filtered a list and
 * deleted whatever it found, which for a non-member is an empty list.
 */
@Transactional
class DeleteMembershipControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}";

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void deletingAMembershipThatDoesNotExistIsReportedAsNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        // Exists, and is deliberately not a member of this community.
        User outsider = persistUser();
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(delete(PATH, community.getId(), outsider.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound())
                // The RestError body, not a silent success and not an empty 500.
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    /**
     * A membership in another community is the case the old list filter was meant to catch and the
     * one most likely to be hit by a mistyped community id: the user exists and is a member
     * somewhere, just not here.
     */
    @Test
    void deletingAMembershipOfAnotherCommunityIsReportedAsNotFoundAndLeavesItAlone() throws Exception {
        CommunityEntity community = persistCommunity();
        CommunityEntity otherCommunity = persistCommunity();
        User member = persistMember(otherCommunity.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(delete(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());

        assertTrue(membershipExists(otherCommunity.getId(), member.getId()),
                "the membership in the other community must not have been touched");
    }

    /**
     * The happy path, so the tests above are known to be failing on the missing membership rather
     * than on the caller being rejected.
     */
    @Test
    void deletingAnExistingMembershipRemovesIt() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(delete(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        assertFalse(membershipExists(community.getId(), member.getId()),
                "the membership should have been deleted");
    }

    /**
     * Deleting the same membership twice: the first call removes it, and the second now reports it
     * is not there rather than repeating the success.
     */
    @Test
    void deletingTheSameMembershipTwiceReportsTheSecondAttemptAsNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(delete(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
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
                .path("delete")
                .path("responses");

        assertTrue(!responses.path("404").isMissingNode(),
                () -> "the DELETE operation declares no 404: " + fieldNames(responses));
    }

    private boolean membershipExists(UUID communityId, UUID userId) {
        return membershipJpaRepository.findByUserIdAndCommunityId(userId, communityId).isPresent();
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
