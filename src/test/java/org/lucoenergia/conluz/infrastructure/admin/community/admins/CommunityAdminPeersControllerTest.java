package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A community's admins manage each other, and the community keeps at least one active admin (#449): admins of a
 * community demote and remove each other (MEM-034), but not the last active admin (MEM-033); the platform admin
 * suspends and reactivates admins, and does not demote or remove them.
 */
class CommunityAdminPeersControllerTest extends BaseCommunityAdminTest {

    private static final String LAST_ACTIVE_ADMIN = "MEMBERSHIP_LAST_ACTIVE_ADMIN";

    @Autowired
    private DisableUserService disableUserService;
    @Autowired
    private CreateSupplyService createSupplyService;

    // --- Peers ---

    @Test
    @DisplayName("MEM-034 with two active admins, one demotes the other")
    void withTwoActiveAdmins_oneDemotesTheOther() throws Exception {
        Community community = newCommunity();
        User first = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User second = memberOf(community, CommunityRole.COMMUNITY_ADMIN);

        demote(loginUser(first), community, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMMUNITY_MEMBER"))
                .andExpect(jsonPath("$.lastActiveAdmin").value(false));

        assertThat(role(community, second)).isEqualTo("COMMUNITY_MEMBER");
        assertThat(lastActiveAdmin(loginUser(first), community, first)).isTrue();
    }

    @Test
    @DisplayName("MEM-034 with two active admins, one removes the other")
    void withTwoActiveAdmins_oneRemovesTheOther() throws Exception {
        Community community = newCommunity();
        User first = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User second = memberOf(community, CommunityRole.COMMUNITY_ADMIN);

        remove(loginUser(first), community, second).andExpect(status().isOk());

        assertThat(membershipExists(community, second)).isFalse();
    }

    @Test
    @DisplayName("MEM-034 with two active admins, one demotes themselves")
    void withTwoActiveAdmins_oneDemotesThemselves() throws Exception {
        Community community = newCommunity();
        User first = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User second = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String bearer = loginUser(first);
        assertThat(lastActiveAdmin(bearer, community, first)).isFalse();

        demote(bearer, community, first).andExpect(status().isOk());

        assertThat(role(community, first)).isEqualTo("COMMUNITY_MEMBER");
        assertThat(role(community, second)).isEqualTo("COMMUNITY_ADMIN");
    }

    // --- The last active admin ---

    @Test
    @DisplayName("MEM-033 the only active admin demoting themselves is refused with 409, and nothing changes")
    void theOnlyActiveAdmin_cannotDemoteThemselves() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String bearer = loginUser(admin);
        Map<String, Object> before = membershipRow(admin.getPersonalId(), community.getId());
        assertThat(lastActiveAdmin(bearer, community, admin)).isTrue();

        expectLastActiveAdmin(demote(bearer, community, admin), community, admin);

        assertThat(membershipRow(admin.getPersonalId(), community.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("MEM-033 the only active admin removing their own membership is refused with 409, and nothing "
            + "changes")
    void theOnlyActiveAdmin_cannotRemoveThemselves() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String bearer = loginUser(admin);
        Map<String, Object> before = membershipRow(admin.getPersonalId(), community.getId());

        expectLastActiveAdmin(remove(bearer, community, admin), community, admin);

        assertThat(membershipRow(admin.getPersonalId(), community.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("MEM-033 an admin with a pending invitation does not count: demoting or removing the only active "
            + "admin is refused")
    void aPendingAdmin_doesNotCount() throws Exception {
        Community community = newCommunity();
        User active = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        InvitedAdmin pending = invitedAdminOf(community, loginAsPlatformAdmin(), newAdminRecord());
        String bearer = loginUser(active);
        assertThat(lastActiveAdmin(bearer, community, active)).isTrue();
        assertThat(lastActiveAdmin(bearer, community, pending.userId())).isFalse();

        expectLastActiveAdmin(demote(bearer, community, active), community, active);
        expectLastActiveAdmin(remove(bearer, community, active), community, active);

        assertThat(role(community, active)).isEqualTo("COMMUNITY_ADMIN");
    }

    @Test
    @DisplayName("MEM-033 a pending admin can be demoted and removed by the only active admin")
    void aPendingAdmin_canBeDemotedAndRemoved() throws Exception {
        Community community = newCommunity();
        User active = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        InvitedAdmin pending = invitedAdminOf(community, loginAsPlatformAdmin(), newAdminRecord());
        String bearer = loginUser(active);

        demote(bearer, community, pending.userId()).andExpect(status().isOk());
        remove(bearer, community, pending.userId()).andExpect(status().isOk());

        assertThat(membershipExists(community, pending.userId())).isFalse();
    }

    @Test
    @DisplayName("MEM-033 a suspended admin does not count: they can be demoted and removed, and the only active "
            + "admin cannot")
    void aSuspendedAdmin_doesNotCount() throws Exception {
        Community community = newCommunity();
        User active = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User suspended = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User suspendedToo = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String platformAdmin = loginAsPlatformAdmin();
        disableAdmin(platformAdmin, community.getId(), suspended.getId()).andExpect(status().isOk());
        disableAdmin(platformAdmin, community.getId(), suspendedToo.getId()).andExpect(status().isOk());
        String bearer = loginUser(active);
        assertThat(lastActiveAdmin(bearer, community, active)).isTrue();
        assertThat(lastActiveAdmin(bearer, community, suspended)).isFalse();

        expectLastActiveAdmin(demote(bearer, community, active), community, active);
        expectLastActiveAdmin(remove(bearer, community, active), community, active);
        demote(bearer, community, suspended).andExpect(status().isOk());
        remove(bearer, community, suspendedToo).andExpect(status().isOk());

        assertThat(role(community, active)).isEqualTo("COMMUNITY_ADMIN");
        assertThat(role(community, suspended)).isEqualTo("COMMUNITY_MEMBER");
        assertThat(membershipExists(community, suspendedToo)).isFalse();
    }

    @Test
    @DisplayName("MEM-033 an admin whose account is disabled does not count: they can be demoted and removed, and "
            + "the only active admin cannot")
    void anAdminWithADisabledAccount_doesNotCount() throws Exception {
        Community community = newCommunity();
        User active = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User disabled = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User disabledToo = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        disableUserService.disable(UserId.of(disabled.getId()));
        disableUserService.disable(UserId.of(disabledToo.getId()));
        String bearer = loginUser(active);
        assertThat(lastActiveAdmin(bearer, community, active)).isTrue();
        assertThat(lastActiveAdmin(bearer, community, disabled)).isFalse();

        expectLastActiveAdmin(demote(bearer, community, active), community, active);
        expectLastActiveAdmin(remove(bearer, community, active), community, active);
        demote(bearer, community, disabled).andExpect(status().isOk());
        remove(bearer, community, disabledToo).andExpect(status().isOk());

        assertThat(role(community, active)).isEqualTo("COMMUNITY_ADMIN");
        assertThat(role(community, disabled)).isEqualTo("COMMUNITY_MEMBER");
        assertThat(membershipExists(community, disabledToo)).isFalse();
    }

    @Test
    @DisplayName("MEM-033 MEM-029 removing the only active admin, who owns a supply in the community, is refused as "
            + "the last active admin, and nothing is deleted")
    void theLastActiveAdminRefusal_answersBeforeTheSupplyOwnerRefusal() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        createSupplyService.create(SupplyMother.random(admin).build(), UserPersonalId.of(admin.getPersonalId()),
                community.getId());

        expectLastActiveAdmin(remove(loginUser(admin), community, admin), community, admin);

        assertThat(membershipExists(community, admin)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM supplies WHERE user_id = ? AND community_id = ?",
                Integer.class, admin.getId(), community.getId())).isEqualTo(1);
    }

    // --- The platform admin ---

    @Test
    @DisplayName("MEM-034 the platform admin suspends the only active admin, and the community is left without one")
    void thePlatformAdmin_suspendsTheOnlyActiveAdmin() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);

        disableAdmin(loginAsPlatformAdmin(), community.getId(), admin.getId()).andExpect(status().isOk());

        assertThat(membershipRow(admin.getPersonalId(), community.getId()).get("enabled")).isEqualTo(false);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM community_memberships WHERE community_id = ? "
                        + "AND role = 'COMMUNITY_ADMIN' AND accepted_at IS NOT NULL AND enabled", Integer.class,
                community.getId())).isZero();
    }

    @Test
    @DisplayName("MEM-034 the platform admin cannot demote an admin, nor delete an admin membership")
    void thePlatformAdmin_cannotDemoteOrRemoveAnAdmin() throws Exception {
        Community community = newCommunity();
        User first = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String platformAdmin = loginAsPlatformAdmin();
        Map<String, Object> before = membershipRow(first.getPersonalId(), community.getId());

        demote(platformAdmin, community, first).andExpect(status().isForbidden());
        remove(platformAdmin, community, first).andExpect(status().isForbidden());

        assertThat(membershipRow(first.getPersonalId(), community.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("MEM-034 the platform admin invites, resends, revokes, suspends and reactivates a community's admins")
    void thePlatformAdmin_managesTheAdminsThroughTheAdminsEndpoints() throws Exception {
        Community community = newCommunity();
        String platformAdmin = loginAsPlatformAdmin();
        InvitedAdmin invited = invitedAdminOf(community, platformAdmin, newAdminRecord());
        User active = memberOf(community, CommunityRole.COMMUNITY_ADMIN);

        resendAdmin(platformAdmin, community.getId(), invited.userId()).andExpect(status().isOk());
        revokeAdmin(platformAdmin, community.getId(), invited.userId()).andExpect(status().isOk());
        disableAdmin(platformAdmin, community.getId(), active.getId()).andExpect(status().isOk());
        enableAdmin(platformAdmin, community.getId(), active.getId()).andExpect(status().isOk());

        assertThat(membershipRow(active.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("MEM-034 a community admin cannot suspend another admin, through the memberships or the admins "
            + "endpoints")
    void aCommunityAdmin_cannotSuspendAnotherAdmin() throws Exception {
        Community community = newCommunity();
        User first = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        User second = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String bearer = loginUser(first);

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/disable", community.getId(),
                        second.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        disableAdmin(bearer, community.getId(), second.getId()).andExpect(status().isForbidden());

        assertThat(membershipRow(second.getPersonalId(), community.getId()).get("enabled")).isEqualTo(true);
    }

    // --- Requests ---

    private ResultActions demote(String bearer, Community community, User user) throws Exception {
        return demote(bearer, community, user.getId());
    }

    private ResultActions demote(String bearer, Community community, UUID userId) throws Exception {
        return mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}", community.getId(),
                userId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"COMMUNITY_MEMBER\"}"));
    }

    private ResultActions remove(String bearer, Community community, User user) throws Exception {
        return remove(bearer, community, user.getId());
    }

    private ResultActions remove(String bearer, Community community, UUID userId) throws Exception {
        return mockMvc.perform(delete("/api/v1/communities/{communityId}/memberships/{userId}", community.getId(),
                userId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private static void expectLastActiveAdmin(ResultActions request, Community community, User user)
            throws Exception {
        request.andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errors[0].code").value(LAST_ACTIVE_ADMIN))
                .andExpect(jsonPath("$.errors[0].params.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.errors[0].params.communityId").value(community.getId().toString()));
    }

    // --- Reading ---

    private boolean lastActiveAdmin(String bearer, Community community, User user) throws Exception {
        return lastActiveAdmin(bearer, community, user.getId());
    }

    private boolean lastActiveAdmin(String bearer, Community community, UUID userId) throws Exception {
        JsonNode roster = objectMapper.readTree(mockMvc.perform(get("/api/v1/communities/{communityId}/memberships",
                        community.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return entryOf(roster, userId).get("lastActiveAdmin").asBoolean();
    }

    private String role(Community community, User user) {
        return role(community, user.getId());
    }

    private String role(Community community, UUID userId) {
        return jdbcTemplate.queryForObject("SELECT role FROM community_memberships WHERE user_id = ? "
                + "AND community_id = ?", String.class, userId, community.getId());
    }

    private boolean membershipExists(Community community, User user) {
        return membershipExists(community, user.getId());
    }

    private boolean membershipExists(Community community, UUID userId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM community_memberships WHERE user_id = ? "
                + "AND community_id = ?", Integer.class, userId, community.getId()) == 1;
    }
}
