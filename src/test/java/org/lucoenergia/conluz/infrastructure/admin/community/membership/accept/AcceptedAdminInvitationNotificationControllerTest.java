package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.email.Email;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Telling a community's admins that an admin invitation was accepted (#466), through both ways of accepting it.
 */
class AcceptedAdminInvitationNotificationControllerTest extends BaseInvitationAcceptanceTest {

    @Test
    @DisplayName("MEM-035 an admin invitation accepted through the link is told to the community's admins, and not to "
            + "the new admin")
    void anAdminInvitationAcceptedThroughTheLink_isToldToTheAdmins() throws Exception {
        Community community = newCommunity();
        User admin = admin(community);
        Map<String, Object> record = adminRecord();
        String token = invitedAdmin(community, record);

        accept(token, newPassword(), "192.0.2.166").andExpect(status().isNoContent());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactly(admin.getEmail());
        assertThat(bodyTo(notifications, admin.getEmail())).contains(record.get("fullName")
                + " ha aceptado la invitación y ya administra " + community.getName() + ".");
        awaitNextEmailsHandedOver();
    }

    @Test
    @DisplayName("MEM-035 an admin invitation accepted while logged in is told to the community's admins, and not to "
            + "the new admin")
    void anAdminInvitationAcceptedWhileLoggedIn_isToldToTheAdmins() throws Exception {
        Community community = newCommunity();
        User admin = admin(community);
        User invited = newUser();
        remember(invited.getPersonalId());
        Map<String, Object> record = adminRecord();
        record.put("personalId", invited.getPersonalId());
        String token = invitedAdmin(community, record);

        acceptAsUser(loginUser(invited), token, "192.0.2.167").andExpect(status().isNoContent());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactly(admin.getEmail())
                .doesNotContain((String) record.get("contactEmail"), invited.getEmail());
        assertThat(bodyTo(notifications, admin.getEmail())).contains(record.get("fullName")
                + " ha aceptado la invitación y ya administra " + community.getName() + ".");
        awaitNextEmailsHandedOver();
    }

    @Test
    @DisplayName("MEM-035 a member's invitation accepted is not a change to the admins, and tells no one")
    void aMemberInvitationAccepted_tellsNoOne() throws Exception {
        Community community = newCommunity();
        User admin = admin(community);
        InvitedAccount member = invitedAccountWithPassword(community, loginUser(admin), newUser());

        acceptAsUser(loginUser(member.user()), member.member().token(), "192.0.2.168")
                .andExpect(status().isNoContent());

        assertThat(nextAdminChangeNotifications()).isEmpty();
    }

    // --- Fixtures ---

    private User admin(Community community) {
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        remember(admin.getPersonalId());
        remember(admin.getPhoneNumber());
        return admin;
    }

    private Map<String, Object> adminRecord() {
        Map<String, Object> record = newAdminRecord();
        record.put("fullName", "Admin Aceptante " + UUID.randomUUID());
        remember((String) record.get("personalId"));
        remember((String) record.get("phoneNumber"));
        return record;
    }

    /**
     * Invites the person of the record as an admin of the community, on behalf of the platform admin, and returns the
     * link they were sent. The admins told about the invitation are left behind.
     */
    private String invitedAdmin(Community community, Map<String, Object> record) throws Exception {
        inviteAdmin(loginAsPlatformAdmin(), community.getId(), record).andExpect(status().isOk());
        String token = tokenFrom(emailTo(awaitNextEmailsHandedOver(), (String) record.get("contactEmail")));
        nextAdminChangeNotifications();
        return token;
    }
}
