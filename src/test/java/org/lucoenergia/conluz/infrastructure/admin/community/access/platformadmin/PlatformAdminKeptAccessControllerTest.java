package org.lucoenergia.conluz.infrastructure.admin.community.access.platformadmin;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation.BasePlatformAdminInvitationTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a platform admin with no membership keeps once its access to users and memberships is closed (#446): the
 * communities and their aggregated data, creating and editing them, every {@code …/admins} endpoint, the platform-admin
 * list and invitations, revoking and granting the role, and its own account.
 */
class PlatformAdminKeptAccessControllerTest extends BasePlatformAdminInvitationTest {

    @Test
    @DisplayName("MEM-031 a platform admin without memberships still lists, reads, creates and edits communities, with their admin names")
    void theCommunitiesStayTheirs() throws Exception {
        String platformAdmin = loginAsPlatformAdmin();
        Community community = newCommunity();
        Map<String, Object> adminRecord = newAdminRecord();
        User admin = newUser();
        adminRecord.put("personalId", admin.getPersonalId());
        inviteAdmin(platformAdmin, community.getId(), adminRecord).andExpect(status().isOk());
        acceptAsUser(loginUser(admin), tokenFrom(awaitNextEmails(1)[0]), "192.0.2.201")
                .andExpect(status().isNoContent());
        memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        JsonNode list = json(perform(platformAdmin, get("/api/v1/communities")).andExpect(status().isOk()));
        JsonNode listed = null;
        for (JsonNode item : list) {
            if (item.get("id").asText().equals(community.getId().toString())) {
                listed = item;
            }
        }
        assertThat(listed).as("the community is listed").isNotNull();
        assertThat(listed.get("adminNames").get(0).asText()).isEqualTo(adminRecord.get("fullName"));
        assertThat(listed.get("memberCount").asInt()).isEqualTo(2);

        JsonNode detail = json(perform(platformAdmin, get("/api/v1/communities/{communityId}", community.getId()))
                .andExpect(status().isOk()));
        assertThat(detail.get("capabilities").get("canRead").asBoolean()).isTrue();
        assertThat(detail.get("capabilities").get("canUpdate").asBoolean()).isTrue();
        assertThat(detail.get("capabilities").get("canManageAdmins").asBoolean()).isTrue();

        String created = json(perform(platformAdmin, post("/api/v1/communities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Kept", "code", "K" + UUID.randomUUID()
                        .toString().substring(0, 8)))))
                .andExpect(status().isOk())).get("id").asText();
        jdbcTemplate.update("DELETE FROM communities WHERE id = ?", UUID.fromString(created));

        perform(platformAdmin, put("/api/v1/communities/{communityId}", community.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed", "code", community.getCode()))))
                .andExpect(status().isOk());
        perform(platformAdmin, post("/api/v1/communities/{communityId}/disable", community.getId()))
                .andExpect(status().isOk());
        perform(platformAdmin, post("/api/v1/communities/{communityId}/enable", community.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEM-021 a platform admin without memberships still uses every .../admins endpoint")
    void everyAdminsEndpointStaysTheirs() throws Exception {
        String platformAdmin = loginAsPlatformAdmin();
        Community community = newCommunity();
        Map<String, Object> pendingRecord = newAdminRecord();
        String pending = json(inviteAdmin(platformAdmin, community.getId(), pendingRecord)
                .andExpect(status().isOk())).get("user").get("id").asText();
        awaitNextEmails(1);
        Map<String, Object> acceptedRecord = newAdminRecord();
        User acceptedAdmin = newUser();
        acceptedRecord.put("personalId", acceptedAdmin.getPersonalId());
        String accepted = json(inviteAdmin(platformAdmin, community.getId(), acceptedRecord)
                .andExpect(status().isOk())).get("user").get("id").asText();
        acceptAsUser(loginUser(acceptedAdmin), tokenFrom(awaitNextEmails(1)[0]), "192.0.2.202")
                .andExpect(status().isNoContent());

        String admins = "/api/v1/communities/{communityId}/admins";
        JsonNode list = json(perform(platformAdmin, get(admins, community.getId())).andExpect(status().isOk()));
        assertThat(list.size()).isEqualTo(2);
        perform(platformAdmin, post(admins + "/{userId}/invitation/resend", community.getId(), pending))
                .andExpect(status().isOk());
        awaitNextEmails(1);
        perform(platformAdmin, post(admins + "/{userId}/invitation/revoke", community.getId(), pending))
                .andExpect(status().isOk());
        perform(platformAdmin, post(admins + "/{userId}/disable", community.getId(), accepted))
                .andExpect(status().isOk());
        perform(platformAdmin, post(admins + "/{userId}/enable", community.getId(), accepted))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ACC-020 a platform admin without memberships still lists platform admins, invites, resends and revokes, and revokes the role")
    void thePlatformAdminRoleManagementStaysTheirs() throws Exception {
        String platformAdmin = loginAsPlatformAdmin();
        User other = newPlatformAdmin();

        JsonNode list = platformAdminList(platformAdmin);
        assertThat(rowOf(list, other.getId())).isNotNull();

        PlatformInvitation invitation = invited(platformAdmin);
        resendPlatformAdminInvitation(platformAdmin, invitation.userId()).andExpect(status().isOk());
        awaitNextEmails(1);
        revokePlatformAdminInvitation(platformAdmin, invitation.userId()).andExpect(status().isOk());

        perform(platformAdmin, post("/api/v1/users/{userId}/revoke-platform-admin", other.getId()))
                .andExpect(status().isOk());
        assertThat(isPlatformAdmin(other.getId())).isFalse();
    }

    @Test
    @DisplayName("ACC-020 a platform admin without memberships still reads and edits its own account and changes its password")
    void theirOwnAccountStaysTheirs() throws Exception {
        User admin = newPlatformAdmin();
        String platformAdmin = loginUser(admin);

        perform(platformAdmin, get("/api/v1/users/current")).andExpect(status().isOk());
        perform(platformAdmin, get("/api/v1/users/{userId}", admin.getId())).andExpect(status().isOk());
        perform(platformAdmin, put("/api/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", newEmail(), "phoneNumber", "622222222"))))
                .andExpect(status().isOk());
        String newPassword = "a brand new password for the platform admin";
        remember(newPassword);
        perform(platformAdmin, put("/api/v1/users/current/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("currentPassword", admin.getPassword(),
                        "newPassword", newPassword))))
                .andExpect(status().isNoContent());
    }

    private ResultActions perform(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
