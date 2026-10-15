package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resending and revoking a community admin's invitation (MEM-022), and refusing to invite a disabled admin membership
 * (MEM-025).
 */
class CommunityAdminInvitationControllerTest extends BaseCommunityAdminTest {

    // --- Resend ---

    @Test
    @DisplayName("MEM-022 a resend sends a new working link to the record's contact email, and stops the previous one")
    void aResend_sendsANewLink() throws Exception {
        InvitedAdmin admin = invitedAdmin();
        clock.advance(Duration.ofHours(1));

        String response = resendAdmin(admin.platformAdmin(), admin.community().getId(), admin.userId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(admin.userId().toString()))
                .andExpect(jsonPath("$.personalId").value(admin.personalId()))
                .andExpect(jsonPath("$.invitedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.invitationExpiresAt")
                        .value(clock.instant().plus(Duration.ofDays(7)).toString()))
                .andReturn().getResponse().getContentAsString();

        MimeMessage email = awaitNextEmails(1)[0];
        assertThat(email.getAllRecipients()).containsExactly(new InternetAddress(admin.contactEmail()));
        assertThat(linkWorks(tokenFrom(email))).isTrue();
        assertThat(linkWorks(admin.token())).isFalse();
        assertThat(fieldNames(objectMapper.readTree(response))).isEqualTo(new java.util.TreeSet<>(ADMIN_FIELDS));
    }

    @Test
    @DisplayName("MEM-022 one line is logged per resend, with the user and the community")
    void aResend_logsOneLine() throws Exception {
        InvitedAdmin admin = invitedAdmin();
        logs.clear();

        resendAdmin(admin.platformAdmin(), admin.community().getId(), admin.userId()).andExpect(status().isOk());
        awaitNextEmails(1);

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Community admin invitation resent"))
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo(
                        "Community admin invitation resent: userId=" + admin.userId() + ", communityId="
                                + admin.community().getId()));
    }

    @Test
    @DisplayName("MEM-022 an admin membership is sent at most 4 invitations in 24 hours; over that it is answered "
            + "429 with Retry-After, and nothing is issued or sent")
    void theResendLimit_applies() throws Exception {
        InvitedAdmin admin = invitedAdmin();
        UUID communityId = admin.community().getId();
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofMinutes(10));
            resendAdmin(admin.platformAdmin(), communityId, admin.userId()).andExpect(status().isOk());
            tokenFrom(awaitNextEmails(1)[0]);
        }
        Instant invitedAt = invitedAt(admin.personalId(), communityId);
        clearInvocations(emailSender);

        MockHttpServletResponse refused = resendAdmin(admin.platformAdmin(), communityId, admin.userId())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn().getResponse();

        // The first send was 30 minutes ago, so it leaves the window in 23 hours and 30 minutes
        assertThat(refused.getHeader(HttpHeaders.RETRY_AFTER))
                .isEqualTo(String.valueOf(Duration.ofHours(23).plusMinutes(30).getSeconds() + 1));
        assertThat(invitationTokenCount(admin.personalId(), communityId)).isEqualTo(4);
        assertThat(invitedAt(admin.personalId(), communityId)).isEqualTo(invitedAt);
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-022 an accepted admin membership is answered 409 as an existing one")
    void anAcceptedAdmin_isAnswered409() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String platformAdmin = loginAsPlatformAdmin();

        resendAdmin(platformAdmin, community.getId(), admin.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"));
        revokeAdmin(platformAdmin, community.getId(), admin.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"));
        assertThat(tokenCount(admin.getPersonalId())).isZero();
    }

    // --- Revoke ---

    @Test
    @DisplayName("MEM-022 a revoke stops the link and keeps the membership pending with its invitation instant")
    void aRevoke_stopsTheLink() throws Exception {
        InvitedAdmin admin = invitedAdmin();
        UUID communityId = admin.community().getId();
        Instant invitedAt = invitedAt(admin.personalId(), communityId);
        clearInvocations(emailSender);
        logs.clear();

        revokeAdmin(admin.platformAdmin(), communityId, admin.userId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.invitedAt").value(invitedAt.toString()))
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());

        assertThat(linkWorks(admin.token())).isFalse();
        assertThat(invitedAt(admin.personalId(), communityId)).isEqualTo(invitedAt);
        verify(emailSender, never()).send(any());
        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Community admin invitation revoked"))
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo(
                        "Community admin invitation revoked: userId=" + admin.userId() + ", communityId="
                                + communityId + ", revoked=true"));
    }

    // --- Only admin memberships ---

    @Test
    @DisplayName("MEM-022 a member's membership is answered 404, as an unknown one, and nothing is issued or revoked")
    void aMembersMembership_isNotFound() throws Exception {
        InvitedMember member = invitedMember();
        UUID communityId = member.community().getId();
        String platformAdmin = loginAsPlatformAdmin();
        List<Map<String, Object>> tokensBefore = tokenRows(member.personalId());
        clearInvocations(emailSender);

        resendAdmin(platformAdmin, communityId, member.userId()).andExpect(status().isNotFound());
        revokeAdmin(platformAdmin, communityId, member.userId()).andExpect(status().isNotFound());
        resendAdmin(platformAdmin, communityId, UUID.randomUUID()).andExpect(status().isNotFound());
        revokeAdmin(platformAdmin, communityId, UUID.randomUUID()).andExpect(status().isNotFound());

        assertThat(tokenRows(member.personalId())).isEqualTo(tokensBefore);
        assertThat(linkWorks(member.token())).isTrue();
        verify(emailSender, never()).send(any());
    }

    @Test
    void anUnknownCommunity_isNotFound() throws Exception {
        String platformAdmin = loginAsPlatformAdmin();

        resendAdmin(platformAdmin, UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound());
        revokeAdmin(platformAdmin, UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // --- Disabled admin memberships (MEM-025) ---

    @Test
    @DisplayName("MEM-025 a disabled admin membership is not sent an invitation again, by the platform admin nor by "
            + "a community admin, and its invitation can still be revoked")
    void aDisabledAdminMembership_isNotInvitedAgain_andCanBeRevoked() throws Exception {
        InvitedAdmin admin = invitedAdmin();
        UUID communityId = admin.community().getId();
        String communityAdmin = loginAsAdminOf(admin.community());
        disableAdmin(admin.platformAdmin(), communityId, admin.userId()).andExpect(status().isOk());
        List<Map<String, Object>> tokensBefore = tokenRows(admin.personalId());
        Map<String, Object> membershipBefore = membershipRow(admin.personalId(), communityId);
        clearInvocations(emailSender);

        resendAdmin(admin.platformAdmin(), communityId, admin.userId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_DISABLED"));
        resend(communityAdmin, communityId, admin.userId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_DISABLED"));
        invite(communityAdmin, communityId, admin.userId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_DISABLED"));

        assertThat(tokenRows(admin.personalId())).isEqualTo(tokensBefore);
        assertThat(membershipRow(admin.personalId(), communityId)).isEqualTo(membershipBefore);
        verify(emailSender, never()).send(any());

        revokeAdmin(admin.platformAdmin(), communityId, admin.userId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        assertThat(linkWorks(admin.token())).isFalse();
    }

    // --- AC7 ---

    @Test
    @DisplayName("MEM-022 a community admin, a member and an anonymous caller are refused, and nothing is issued or "
            + "revoked")
    void anyoneButThePlatformAdmin_isRefused() throws Exception {
        InvitedAdmin admin = invitedAdmin();
        UUID communityId = admin.community().getId();
        String communityAdmin = loginAsAdminOf(admin.community());
        String member = loginAsMemberOf(admin.community());
        List<Map<String, Object>> tokensBefore = tokenRows(admin.personalId());
        clearInvocations(emailSender);

        for (String bearer : List.of(communityAdmin, member)) {
            resendAdmin(bearer, communityId, admin.userId()).andExpect(status().isForbidden());
            revokeAdmin(bearer, communityId, admin.userId()).andExpect(status().isForbidden());
        }
        resendAdmin(communityAdmin, UUID.randomUUID(), admin.userId()).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/communities/{communityId}/admins/{userId}/invitation/resend", communityId,
                admin.userId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/communities/{communityId}/admins/{userId}/invitation/revoke", communityId,
                admin.userId())).andExpect(status().isUnauthorized());

        assertThat(tokenRows(admin.personalId())).isEqualTo(tokensBefore);
        assertThat(linkWorks(admin.token())).isTrue();
        verify(emailSender, never()).send(any());
    }
}
