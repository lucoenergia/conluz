package org.lucoenergia.conluz.infrastructure.admin.community.membership;

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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Writing an investment is restricted to community admins of the community in question. Unlike the
 * other membership endpoints, a platform admin who does not administer this community is refused,
 * and every refusal of an authenticated caller is a 404: a 403 would confirm the membership is
 * there, which is the thing being protected.
 */
@Transactional
class SetMembershipInvestmentControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/investment";

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    // --- AC1: a community admin records an investment, and it is persisted ---

    @Test
    void aCommunityAdminRecordsTheInvestment() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(put(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": 1500.00}"))
                .andExpect(status().isNoContent());

        assertEquals(0, new BigDecimal("1500.00").compareTo(storedInvestment(community.getId(), member.getId())));
    }

    /**
     * A second write replaces the first rather than accumulating: only the current value is kept.
     */
    @Test
    void recordingAnInvestmentTwiceKeepsOnlyTheLatestAmount() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        setInvestment(community.getId(), member.getId(), adminToken, "1500.00");
        setInvestment(community.getId(), member.getId(), adminToken, "2000.50");

        assertEquals(0, new BigDecimal("2000.50").compareTo(storedInvestment(community.getId(), member.getId())));
    }

    // --- DELETE clears it ---

    @Test
    void aCommunityAdminClearsTheInvestment() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());
        setInvestment(community.getId(), member.getId(), adminToken, "1500.00");

        mockMvc.perform(delete(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        assertNull(storedInvestment(community.getId(), member.getId()));
    }

    /**
     * The end state is what the caller asked for, so there is nothing to report as a failure.
     */
    @Test
    void clearingAnInvestmentThatWasNeverRecordedSucceeds() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(delete(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        assertNull(storedInvestment(community.getId(), member.getId()));
    }

    // --- AC2: the authorization matrix, one test per row ---

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(put(PATH, community.getId(), member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": 1500.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aNonAdminMemberOfTheCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String memberToken = loginAsCommunityMember(community.getId());

        mockMvc.perform(put(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": 1500.00}"))
                .andExpect(status().isNotFound());

        assertNull(storedInvestment(community.getId(), member.getId()));
    }

    @Test
    void anAdminOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        CommunityEntity otherCommunity = persistCommunity();
        User member = persistMember(community.getId());
        String otherAdminToken = loginAsCommunityAdmin(otherCommunity.getId());

        mockMvc.perform(put(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": 1500.00}"))
                .andExpect(status().isNotFound());

        assertNull(storedInvestment(community.getId(), member.getId()));
    }

    /**
     * The one row that distinguishes this guard from {@code canManageMemberships}, which would let
     * this caller through.
     */
    @Test
    void aPlatformAdminWhoDoesNotAdministerTheCommunityIsAnsweredNotFound() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(put(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": 1500.00}"))
                .andExpect(status().isNotFound());

        assertNull(storedInvestment(community.getId(), member.getId()));
    }

    /**
     * Being a platform admin neither grants nor removes access: what counts is the community-admin
     * membership, which this caller also holds.
     */
    @Test
    void aPlatformAdminWhoAlsoAdministersTheCommunityRecordsTheInvestment() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        createMembershipService.create(community.getId(), platformAdminId(platformAdminToken),
                CommunityRole.COMMUNITY_ADMIN);
        // Re-login so the principal carries the membership just granted.
        String refreshedToken = loginAsDefaultPlatformAdmin();

        mockMvc.perform(put(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, refreshedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": 1500.00}"))
                .andExpect(status().isNoContent());

        assertEquals(0, new BigDecimal("1500.00").compareTo(storedInvestment(community.getId(), member.getId())));
    }

    @Test
    void anAllowedCallerTargetingAMembershipThatDoesNotExistIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        // Exists, but is not a member of this community.
        User outsider = persistUser();
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(put(PATH, community.getId(), outsider.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": 1500.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void clearingAMembershipThatDoesNotExistIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User outsider = persistUser();
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(delete(PATH, community.getId(), outsider.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    // --- AC3: rejected amounts ---

    @Test
    void aNegativeAmountIsRejected() throws Exception {
        assertAmountIsRejected("-1");
    }

    @Test
    void anAmountOfZeroIsRejected() throws Exception {
        assertAmountIsRejected("0");
    }

    @Test
    void aNonNumericAmountIsRejected() throws Exception {
        assertAmountIsRejected("\"abc\"");
    }

    @Test
    void anAmountWithThreeDecimalsIsRejected() throws Exception {
        assertAmountIsRejected("1500.123");
    }

    @Test
    void aMissingAmountIsRejected() throws Exception {
        assertAmountIsRejected("null");
    }

    /**
     * Every rejected amount answers 400 with the project's error body, and none of them reaches
     * the column.
     */
    private void assertAmountIsRejected(String rawJsonValue) throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(put(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": " + rawJsonValue + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());

        assertNull(storedInvestment(community.getId(), member.getId()));
    }

    // --- helpers ---

    private void setInvestment(UUID communityId, UUID userId, String token, String amount) throws Exception {
        mockMvc.perform(put(PATH, communityId, userId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": " + amount + "}"))
                .andExpect(status().isNoContent());
    }

    private BigDecimal storedInvestment(UUID communityId, UUID userId) {
        return membershipJpaRepository.findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow()
                .getInvestmentEur();
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

    /**
     * The JWS subject is the user id, which is how the default platform admin's id is reached from
     * their token without a lookup by personal id.
     */
    private UUID platformAdminId(String bearerToken) throws Exception {
        String claims = bearerToken.replace("Bearer ", "").split("\\.")[1];
        String payload = new String(Base64.getUrlDecoder().decode(claims), StandardCharsets.UTF_8);
        return UUID.fromString(objectMapper.readTree(payload).path("sub").asText());
    }
}
