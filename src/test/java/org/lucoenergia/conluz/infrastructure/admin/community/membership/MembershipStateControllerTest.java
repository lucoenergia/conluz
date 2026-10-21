package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseRegisterMemberTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A community admin enabling and disabling the memberships of their community's members (MEM-027): only that
 * membership is written, never the account, so the member loses and regains that community alone.
 */
class MembershipStateControllerTest extends BaseRegisterMemberTest {

    @Test
    @DisplayName("MEM-027 a disabled member loses the community from their next request and keeps their other "
            + "community; enabling restores it; the account is not written")
    void disabling_removesAccessToThatCommunityOnly_andEnablingRestoresIt() throws Exception {
        Community first = newCommunity();
        Community second = newCommunity();
        User member = memberOf(first, CommunityRole.COMMUNITY_MEMBER);
        createMembershipRepository.create(second.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        String bearer = loginUser(member);
        String admin = loginAsAdminOf(first);
        Map<String, Object> accountBefore = accountRow(member.getPersonalId());
        community(bearer, first.getId()).andExpect(status().isOk());

        disable(admin, first.getId(), member.getId())
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        community(bearer, first.getId()).andExpect(status().isNotFound());
        community(bearer, second.getId()).andExpect(status().isOk());
        assertThat(membershipRow(member.getPersonalId(), first.getId()).get("enabled")).isEqualTo(false);
        assertThat(membershipRow(member.getPersonalId(), second.getId()).get("enabled")).isEqualTo(true);
        assertThat(accountRow(member.getPersonalId())).isEqualTo(accountBefore);
        assertThat(accountRow(member.getPersonalId()).get("enabled")).isEqualTo(true);

        enable(admin, first.getId(), member.getId()).andExpect(status().isOk());

        community(bearer, first.getId()).andExpect(status().isOk());
        community(bearer, second.getId()).andExpect(status().isOk());
        assertThat(membershipRow(member.getPersonalId(), first.getId()).get("enabled")).isEqualTo(true);
        assertThat(accountRow(member.getPersonalId())).isEqualTo(accountBefore);
    }

    @Test
    @DisplayName("MEM-027 enabling and disabling are idempotent")
    void enablingAndDisabling_areIdempotent() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String admin = loginAsAdminOf(community);

        enable(admin, community.getId(), member.getId()).andExpect(status().isOk());
        enable(admin, community.getId(), member.getId()).andExpect(status().isOk());
        assertThat(membershipRow(member.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
        disable(admin, community.getId(), member.getId()).andExpect(status().isOk());
        disable(admin, community.getId(), member.getId()).andExpect(status().isOk());
        assertThat(membershipRow(member.getPersonalId(), community.getId()).get("enabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("MEM-027 a pending member's membership can be disabled and enabled, and stays pending")
    void aPendingMember_canBeDisabledAndEnabled() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        Map<String, Object> record = newRecord();
        UUID userId = registered(admin, community.getId(), record);
        String personalId = (String) record.get("personalId");

        disable(admin, community.getId(), userId).andExpect(status().isOk());

        Map<String, Object> membership = membershipRow(personalId, community.getId());
        assertThat(membership.get("enabled")).isEqualTo(false);
        assertThat(membership.get("accepted_at")).isNull();

        enable(admin, community.getId(), userId).andExpect(status().isOk());

        membership = membershipRow(personalId, community.getId());
        assertThat(membership.get("enabled")).isEqualTo(true);
        assertThat(membership.get("accepted_at")).isNull();
    }

    @Test
    @DisplayName("MEM-027 one line is logged per change, with the user and the community")
    void oneLineIsLogged() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String admin = loginAsAdminOf(community);
        logs.clear();

        disable(admin, community.getId(), member.getId()).andExpect(status().isOk());
        enable(admin, community.getId(), member.getId()).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Membership "))
                .extracting(event -> event.getFormattedMessage())
                .containsExactly(
                        "Membership disabled: userId=" + member.getId() + ", communityId=" + community.getId(),
                        "Membership enabled: userId=" + member.getId() + ", communityId=" + community.getId());
    }

    // --- Only member memberships of the community ---

    @Test
    @DisplayName("MEM-027 an admin's membership is answered 404, as an unknown one, and is not written")
    void anAdminsMembership_isNotFound() throws Exception {
        Community community = newCommunity();
        User otherAdmin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String admin = loginAsAdminOf(community);

        disable(admin, community.getId(), otherAdmin.getId()).andExpect(status().isNotFound());
        enable(admin, community.getId(), otherAdmin.getId()).andExpect(status().isNotFound());

        assertThat(membershipRow(otherAdmin.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("MEM-027 an unknown user, and a member of another community, are answered 404, and nothing is written")
    void aMembershipThatIsNotInTheCommunity_isNotFound() throws Exception {
        Community community = newCommunity();
        Community other = newCommunity();
        User memberOfOther = memberOf(other, CommunityRole.COMMUNITY_MEMBER);
        String admin = loginAsAdminOf(community);

        disable(admin, community.getId(), UUID.randomUUID()).andExpect(status().isNotFound());
        enable(admin, community.getId(), UUID.randomUUID()).andExpect(status().isNotFound());
        disable(admin, community.getId(), memberOfOther.getId()).andExpect(status().isNotFound());
        enable(admin, community.getId(), memberOfOther.getId()).andExpect(status().isNotFound());

        assertThat(membershipRow(memberOfOther.getPersonalId(), other.getId()).get("enabled")).isEqualTo(true);
    }

    @Test
    void anUnknownCommunity_isNotFound() throws Exception {
        String admin = loginAsAdminOf(newCommunity());

        disable(admin, UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound());
        enable(admin, UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // --- Only an active admin of the community ---

    @Test
    @DisplayName("MEM-027 a platform admin and a member are refused with 403, and nothing is written")
    void aPlatformAdminAndAMember_areForbidden() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        for (String bearer : List.of(loginAsPlatformAdmin(), loginAsMemberOf(community), loginUser(member))) {
            disable(bearer, community.getId(), member.getId()).andExpect(status().isForbidden());
            enable(bearer, community.getId(), member.getId()).andExpect(status().isForbidden());
        }

        assertThat(membershipRow(member.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("MEM-027 a pending admin, a disabled admin and an admin of another community are answered 404, "
            + "and nothing is written")
    void anAdminWhoCannotSeeTheCommunity_isNotFound() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        for (String bearer : List.of(loginAsPendingAdminOf(community), loginAsDisabledAdminOf(community),
                loginAsAdminOf(newCommunity()))) {
            disable(bearer, community.getId(), member.getId()).andExpect(status().isNotFound());
            enable(bearer, community.getId(), member.getId()).andExpect(status().isNotFound());
        }

        assertThat(membershipRow(member.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/disable", community.getId(),
                member.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/enable", community.getId(),
                member.getId())).andExpect(status().isUnauthorized());
    }

    private ResultActions enable(String bearer, UUID communityId, UUID userId) throws Exception {
        return mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/enable", communityId,
                userId).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions disable(String bearer, UUID communityId, UUID userId) throws Exception {
        return mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/disable", communityId,
                userId).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /**
     * Reading the community: an endpoint any active member of it may call.
     */
    private ResultActions community(String bearer, UUID communityId) throws Exception {
        return mockMvc.perform(get("/api/v1/communities/{communityId}", communityId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
