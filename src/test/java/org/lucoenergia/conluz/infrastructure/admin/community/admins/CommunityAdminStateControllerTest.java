package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
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
 * Enabling and disabling a community admin's membership (MEM-023): only that membership is written, so the admin
 * loses and regains access to that community alone.
 */
class CommunityAdminStateControllerTest extends BaseCommunityAdminTest {

    // --- AC5 ---

    @Test
    @DisplayName("MEM-023 a disabled admin loses access to the community from their next request and keeps their "
            + "other community; enabling restores it; the account is not written")
    void disabling_removesAccessToThatCommunityOnly_andEnablingRestoresIt() throws Exception {
        Community first = newCommunity();
        Community second = newCommunity();
        User admin = memberOf(first, CommunityRole.COMMUNITY_ADMIN);
        createMembershipService.create(second.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        String bearer = loginUser(admin);
        String platformAdmin = loginAsPlatformAdmin();
        Map<String, Object> accountBefore = accountRow(admin.getPersonalId());
        roster(bearer, first.getId()).andExpect(status().isOk());

        disableAdmin(platformAdmin, first.getId(), admin.getId())
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        roster(bearer, first.getId()).andExpect(status().isNotFound());
        roster(bearer, second.getId()).andExpect(status().isOk());
        assertThat(membershipRow(admin.getPersonalId(), first.getId()).get("enabled")).isEqualTo(false);
        assertThat(membershipRow(admin.getPersonalId(), second.getId()).get("enabled")).isEqualTo(true);
        assertThat(accountRow(admin.getPersonalId())).isEqualTo(accountBefore);

        enableAdmin(platformAdmin, first.getId(), admin.getId()).andExpect(status().isOk());

        roster(bearer, first.getId()).andExpect(status().isOk());
        roster(bearer, second.getId()).andExpect(status().isOk());
        assertThat(membershipRow(admin.getPersonalId(), first.getId()).get("enabled")).isEqualTo(true);
        assertThat(accountRow(admin.getPersonalId())).isEqualTo(accountBefore);
    }

    @Test
    @DisplayName("MEM-023 enabling and disabling are idempotent")
    void enablingAndDisabling_areIdempotent() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String platformAdmin = loginAsPlatformAdmin();

        enableAdmin(platformAdmin, community.getId(), admin.getId()).andExpect(status().isOk());
        assertThat(membershipRow(admin.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
        disableAdmin(platformAdmin, community.getId(), admin.getId()).andExpect(status().isOk());
        disableAdmin(platformAdmin, community.getId(), admin.getId()).andExpect(status().isOk());
        assertThat(membershipRow(admin.getPersonalId(), community.getId()).get("enabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("MEM-023 a pending admin membership can be disabled, and stays pending")
    void aPendingAdmin_canBeDisabled() throws Exception {
        InvitedAdmin admin = invitedAdmin();

        disableAdmin(admin.platformAdmin(), admin.community().getId(), admin.userId()).andExpect(status().isOk());

        Map<String, Object> membership = membershipRow(admin.personalId(), admin.community().getId());
        assertThat(membership.get("enabled")).isEqualTo(false);
        assertThat(membership.get("accepted_at")).isNull();
    }

    @Test
    @DisplayName("MEM-023 one line is logged per change, with the user and the community")
    void oneLineIsLogged() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String platformAdmin = loginAsPlatformAdmin();
        logs.clear();

        disableAdmin(platformAdmin, community.getId(), admin.getId()).andExpect(status().isOk());
        enableAdmin(platformAdmin, community.getId(), admin.getId()).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Community admin "))
                .extracting(event -> event.getFormattedMessage())
                .containsExactly(
                        "Community admin disabled: userId=" + admin.getId() + ", communityId=" + community.getId(),
                        "Community admin enabled: userId=" + admin.getId() + ", communityId=" + community.getId());
    }

    // --- Only admin memberships ---

    @Test
    @DisplayName("MEM-023 a member's membership is answered 404, as an unknown one, and is not written")
    void aMembersMembership_isNotFound() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String platformAdmin = loginAsPlatformAdmin();

        disableAdmin(platformAdmin, community.getId(), member.getId()).andExpect(status().isNotFound());
        enableAdmin(platformAdmin, community.getId(), member.getId()).andExpect(status().isNotFound());
        disableAdmin(platformAdmin, community.getId(), UUID.randomUUID()).andExpect(status().isNotFound());

        assertThat(membershipRow(member.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
    }

    @Test
    void anUnknownCommunity_isNotFound() throws Exception {
        String platformAdmin = loginAsPlatformAdmin();

        disableAdmin(platformAdmin, UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound());
        enableAdmin(platformAdmin, UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // --- AC7 ---

    @Test
    @DisplayName("MEM-023 a community admin, a member and an anonymous caller are refused, and nothing is written")
    void anyoneButThePlatformAdmin_isRefused() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String otherAdmin = loginAsAdminOf(community);
        String member = loginAsMemberOf(community);

        for (String bearer : List.of(otherAdmin, member, loginUser(admin))) {
            disableAdmin(bearer, community.getId(), admin.getId()).andExpect(status().isForbidden());
            enableAdmin(bearer, community.getId(), admin.getId()).andExpect(status().isForbidden());
        }
        disableAdmin(otherAdmin, UUID.randomUUID(), admin.getId()).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/communities/{communityId}/admins/{userId}/disable", community.getId(),
                admin.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/communities/{communityId}/admins/{userId}/enable", community.getId(),
                admin.getId())).andExpect(status().isUnauthorized());

        assertThat(membershipRow(admin.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
    }

    /**
     * The community's roster: an endpoint only an active community admin of the community, or the platform admin,
     * may read.
     */
    private ResultActions roster(String bearer, UUID communityId) throws Exception {
        return mockMvc.perform(get("/api/v1/communities/{communityId}/memberships", communityId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
