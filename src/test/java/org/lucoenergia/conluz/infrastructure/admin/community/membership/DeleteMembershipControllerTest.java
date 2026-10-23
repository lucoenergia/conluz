package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisterMemberService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private RegisterMemberService registerMemberService;
    @Autowired
    private CreateOneTimeTokenService createOneTimeTokenService;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

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

    @Test
    @DisplayName("MEM-029 deleting the membership of a member who owns a supply in the community is refused with 409 "
            + "MEMBERSHIP_OWNS_SUPPLIES, and nothing is deleted")
    void deletingTheMembershipOfASupplyOwnerInTheCommunityIsRefused() throws Exception {
        UUID community = newCommunity();
        User member = registerInvitedMember(community);
        Supply supply = createSupply(member, community);
        String adminToken = loginAsCommunityAdmin(community);

        mockMvc.perform(delete(PATH, community, member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_OWNS_SUPPLIES"))
                .andExpect(jsonPath("$.errors[0].params.userId").value(member.getId().toString()))
                .andExpect(jsonPath("$.errors[0].params.communityId").value(community.toString()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        assertTrue(membershipExists(community, member.getId()), "the membership must not have been deleted");
        assertEquals(1, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", member.getId()));
        assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ? AND user_id = ? AND community_id = ?",
                supply.getId(), member.getId(), community));
    }

    @Test
    @DisplayName("MEM-029 supplies the member owns in another community do not prevent deleting their membership, and "
            + "are left alone")
    void suppliesInAnotherCommunityDoNotPreventDeletingTheMembership() throws Exception {
        UUID community = newCommunity();
        UUID otherCommunity = newCommunity();
        User member = registerInvitedMember(community);
        createMembershipRepository.create(otherCommunity, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply supply = createSupply(member, otherCommunity);
        String adminToken = loginAsCommunityAdmin(community);

        mockMvc.perform(delete(PATH, community, member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        assertFalse(membershipExists(community, member.getId()), "the membership should have been deleted");
        assertEquals(0, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", member.getId()));
        assertTrue(membershipExists(otherCommunity, member.getId()),
                "the membership in the other community must not have been touched");
        assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ? AND user_id = ? AND community_id = ?",
                supply.getId(), member.getId(), otherCommunity));
    }

    @Test
    @DisplayName("MEM-029 deleting the membership of a member who owns no supply deletes it with its invitation tokens, "
            + "and no other token")
    void deletingTheMembershipOfAMemberWithoutSuppliesDeletesItAndItsTokens() throws Exception {
        UUID community = newCommunity();
        User member = registerInvitedMember(community);
        User other = registerInvitedMember(community);
        String adminToken = loginAsCommunityAdmin(community);

        mockMvc.perform(delete(PATH, community, member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        assertFalse(membershipExists(community, member.getId()), "the membership should have been deleted");
        assertEquals(0, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", member.getId()));
        assertEquals(1, count("SELECT count(*) FROM users WHERE id = ?", member.getId()));
        assertEquals(1, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", other.getId()));
    }

    @Test
    void theContractDeclaresTheConflictOfDeletingTheMembershipOfASupplyOwner() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/communities/{communityId}/memberships/{userId}'].delete"
                        + ".responses['409'].content['application/json'].examples.MEMBERSHIP_OWNS_SUPPLIES.value"
                        + ".errors[0].code")
                        .value("MEMBERSHIP_OWNS_SUPPLIES"));
    }

    private UUID newCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build()).getId();
    }

    /**
     * A member registered through the registration flow, with a pending membership and an invitation token.
     */
    private User registerInvitedMember(UUID communityId) {
        User member = persistUser();
        registerMemberService.register(communityId, UserId.of(persistUser().getId()),
                UserPersonalId.of(member.getPersonalId()),
                new MemberRecord(member.getFullName(), null, "member-" + UUID.randomUUID() + "@example.org", null,
                        null));
        createOneTimeTokenService.issueForMembership(UserId.of(member.getId()), communityId,
                OneTimeTokenPurpose.INVITATION);
        return member;
    }

    private Supply createSupply(User owner, UUID communityId) {
        return createSupplyService.create(SupplyMother.random(owner).build(), UserPersonalId.of(owner.getPersonalId()),
                communityId);
    }

    /**
     * Counts with plain SQL, after flushing what the request left in the persistence context: the test's transaction
     * is still open, and what is asserted is what the database holds.
     */
    private int count(String sql, Object... parameters) {
        entityManager.flush();
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parameters);
        return count == null ? 0 : count;
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
        createMembershipRepository.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }
}
