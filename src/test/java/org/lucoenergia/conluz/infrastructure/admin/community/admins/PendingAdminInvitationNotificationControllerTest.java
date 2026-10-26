package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.infrastructure.admin.community.admins.notification.CommunityAdminChangeEmailFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A community admin sending, resending or revoking a pending admin's invitation through the member endpoints
 * (#482): the send and the resend are told to the community's other admins as the platform admin's are (#466), the
 * revoke is not, and a member's invitation is never told.
 */
class PendingAdminInvitationNotificationControllerTest extends BaseCommunityAdminTest {

    private static final String WARN_PREFIX = "Admin change ";

    /** The full names of the people of each test: they may appear in an email body, never in a log line. */
    private final List<String> names = new ArrayList<>();

    /** A pending membership, and the full name of its member record. */
    private record Pending(UUID userId, String fullName, String contactEmail) {
    }

    @AfterEach
    void noLogLineContainsAName() {
        for (ILoggingEvent event : logs.all()) {
            if (event.getLoggerName().startsWith("com.icegreen.greenmail")) {
                continue;
            }
            for (String name : names) {
                assertThat(event.getFormattedMessage()).as("a line logged by %s", event.getLoggerName())
                        .doesNotContain(name);
            }
        }
    }

    // --- Send and resend ---

    @Test
    @DisplayName("MEM-035 a community admin sending a pending admin's first invitation is told to the other admins, "
            + "not to the actor nor to the invited admin")
    void aFirstInvitationOfAPendingAdmin_isToldToTheOtherAdmins() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);
        String bearer = loginUser(actor);
        Pending pending = pendingAdmin(community, bearer, "Invitada Pendiente");

        invite(bearer, community.getId(), pending.userId()).andExpect(status().isOk());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactly(other.getEmail());
        assertThat(bodyTo(notifications, other.getEmail())).contains(actor.getFullName() + " ha invitado a "
                + pending.fullName() + " a administrar " + community.getName() + ".");
        tokenFrom(emailTo(awaitNextEmailsHandedOver(), pending.contactEmail()));
    }

    @Test
    @DisplayName("MEM-035 a community admin resending a pending admin's invitation is told to the other admins, not "
            + "to the actor nor to the invited admin")
    void aResendToAPendingAdmin_isToldToTheOtherAdmins() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);
        String bearer = loginUser(actor);
        Pending pending = pendingAdmin(community, bearer, "Reenviada Pendiente");
        invite(bearer, community.getId(), pending.userId()).andExpect(status().isOk());
        nextAdminChangeNotifications();
        awaitNextEmailsHandedOver();

        resend(bearer, community.getId(), pending.userId()).andExpect(status().isOk());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactly(other.getEmail());
        assertThat(bodyTo(notifications, other.getEmail())).contains(actor.getFullName() + " ha vuelto a enviar a "
                + pending.fullName() + " la invitación para administrar " + community.getName() + ".");
        tokenFrom(emailTo(awaitNextEmailsHandedOver(), pending.contactEmail()));
    }

    @Test
    @DisplayName("MEM-035 a bulk send to a pending admin and two pending members tells the other admins about the "
            + "admin only, once")
    void aBulkSend_isToldAboutTheAdminOnly() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User first = admin(community);
        User second = admin(community);
        String bearer = loginUser(actor);
        Pending pending = pendingAdmin(community, bearer, "Masiva Pendiente");
        pendingMember(community, bearer, "Socia Uno");
        pendingMember(community, bearer, "Socia Dos");

        sendInvitations(bearer, community.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(3));

        // One email per other admin, so a single change
        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactlyInAnyOrder(first.getEmail(), second.getEmail());
        assertThat(notifications).allSatisfy(email -> assertThat(email.body()).contains(actor.getFullName()
                + " ha invitado a " + pending.fullName() + " a administrar " + community.getName() + "."));
        assertThat(awaitNextEmailsHandedOver()).hasSize(5);
    }

    // --- Revoke ---

    @Test
    @DisplayName("MEM-034 a community admin may revoke a pending admin's invitation, which is not told to anyone, and "
            + "the link stops working")
    void aRevokeOfAPendingAdmin_isNotTold() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        admin(community);
        InvitedAdmin invited = invitedAdminOf(community, loginAsPlatformAdmin(), adminRecord("Revocada Pendiente"));
        nextAdminChangeNotifications();

        revoke(loginUser(actor), community.getId(), invited.userId()).andExpect(status().isOk());

        assertThat(nextAdminChangeNotifications()).isEmpty();
        assertThat(linkWorks(invited.token())).isFalse();
    }

    // --- Members ---

    @Test
    @DisplayName("MEM-035 a community admin sending, resending and revoking a member's invitation tells no one")
    void aMembersInvitation_isNotTold() throws Exception {
        Community community = newCommunity();
        String bearer = loginUser(admin(community));
        admin(community);
        Pending single = pendingMember(community, bearer, "Socia Individual");
        Pending bulk = pendingMember(community, bearer, "Socia Masiva");

        invite(bearer, community.getId(), single.userId()).andExpect(status().isOk());
        resend(bearer, community.getId(), single.userId()).andExpect(status().isOk());
        revoke(bearer, community.getId(), single.userId()).andExpect(status().isOk());
        sendInvitations(bearer, community.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1));

        assertThat(nextAdminChangeNotifications()).isEmpty();
        MimeMessage[] sent = awaitNextEmailsHandedOver();
        assertThat(sent).hasSize(3);
        tokenFrom(emailTo(sent, bulk.contactEmail()));
    }

    // --- A failure never changes the outcome ---

    @Test
    @DisplayName("MEM-035 a community admin's send, bulk send, resend and revoke of a pending admin's invitation, and "
            + "an edit of its contact email, succeed when the notification cannot be handed over")
    void everyInvitationSucceeds_whenTheNotificationFails() throws Exception {
        Community community = newCommunity();
        String bearer = loginUser(admin(community));
        admin(community);
        Pending single = pendingAdmin(community, bearer, "Fallida Individual");
        Pending bulk = pendingAdmin(community, bearer, "Fallida Masiva");
        doThrow(new IllegalStateException("sending failed")).when(emailSender)
                .send(argThat(email -> email != null && CommunityAdminChangeEmailFactory.CATEGORY.equals(
                        email.category())));
        logs.clear();

        invite(bearer, community.getId(), single.userId()).andExpect(status().isOk());
        resend(bearer, community.getId(), single.userId()).andExpect(status().isOk());
        revoke(bearer, community.getId(), single.userId()).andExpect(status().isOk());
        sendInvitations(bearer, community.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1));
        String newEmail = newEmail();
        mockMvc.perform(put("/api/v1/communities/{communityId}/memberships/{userId}/record", community.getId(),
                        bulk.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", bulk.fullName(),
                                "contactEmail", newEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());
        resend(bearer, community.getId(), bulk.userId()).andExpect(status().isOk());

        assertThat(logs.all())
                .filteredOn(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().startsWith(WARN_PREFIX))
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(failure("INVITED", community), failure("INVITATION_RESENT", community),
                        failure("INVITED", community), failure("INVITATION_RESENT", community));
        // The invitations still leave
        MimeMessage[] sent = awaitNextEmails(4);
        assertThat(sent).extracting(email -> email.getAllRecipients()[0].toString())
                .containsExactlyInAnyOrder(single.contactEmail(), single.contactEmail(), bulk.contactEmail(),
                        newEmail);
    }

    // --- Fixtures ---

    /**
     * An active admin of the community, with an enabled account and a contact email.
     */
    private User admin(Community community) {
        User user = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        remember(user.getPersonalId());
        remember(user.getPhoneNumber());
        names.add(user.getFullName());
        return user;
    }

    /**
     * A member registered by the community admin and never invited.
     */
    private Pending pendingMember(Community community, String bearer, String fullName) throws Exception {
        Map<String, Object> record = newRecord();
        record.put("fullName", fullName + " " + UUID.randomUUID());
        remember((String) record.get("personalId"));
        remember((String) record.get("phoneNumber"));
        names.add((String) record.get("fullName"));
        UUID userId = registered(bearer, community.getId(), record);
        return new Pending(userId, (String) record.get("fullName"), (String) record.get("contactEmail"));
    }

    /**
     * A pending admin never invited: a member registered by the community admin and promoted before accepting. The
     * promotion's own notification is read here.
     */
    private Pending pendingAdmin(Community community, String bearer, String fullName) throws Exception {
        Pending pending = pendingMember(community, bearer, fullName);
        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}", community.getId(),
                        pending.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + CommunityRole.COMMUNITY_ADMIN.name() + "\"}"))
                .andExpect(status().isOk());
        nextAdminChangeNotifications();
        awaitNextEmailsHandedOver();
        return pending;
    }

    private Map<String, Object> adminRecord(String fullName) {
        Map<String, Object> record = newAdminRecord();
        record.put("fullName", fullName + " " + UUID.randomUUID());
        remember((String) record.get("personalId"));
        remember((String) record.get("phoneNumber"));
        names.add((String) record.get("fullName"));
        return record;
    }

    private static String failure(String change, Community community) {
        return WARN_PREFIX + change + " in community " + community.getId() + " not notified: IllegalStateException";
    }
}
