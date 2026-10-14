package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseRegisterMemberTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resending, revoking and bulk sending invitations on a server that cannot send them. Each subclass configures one
 * reason, in a context of its own, and the warning it is logged with.
 */
abstract class BaseInvitationUnavailableTest extends BaseRegisterMemberTest {

    @Autowired
    private CreateOneTimeTokenService createOneTimeTokenService;
    @Autowired
    private GetOneTimeTokenService getOneTimeTokenService;
    @Autowired
    private UpdateMembershipInvitationRepository updateMembershipInvitationRepository;

    /**
     * @return the one warning a refusal logs
     */
    protected abstract String expectedWarning();

    /**
     * A member invited the way the invitation flow records it. This server cannot send, so the token is issued and
     * the invitation recorded directly, without the email.
     */
    private record Invited(Community community, String bearer, UUID userId, String personalId, String token) {
    }

    private Invited invited() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        UUID userId = registered(bearer, community.getId(), body);
        String token = createOneTimeTokenService.issueForMembership(UserId.of(userId), community.getId(),
                OneTimeTokenPurpose.INVITATION).value();
        remember(token);
        updateMembershipInvitationRepository.markInvited(UserId.of(userId), community.getId(), clock.instant());
        return new Invited(community, bearer, userId, (String) body.get("personalId"), token);
    }

    @Test
    @DisplayName("MEM-010 a resend is refused with 503, nothing is issued or sent, the invitation instant and the "
            + "link stay as they were, and one warning names only the email category")
    void aResend_isRefused() throws Exception {
        Invited member = invited();
        Instant invitedAt = invitedAt(member);
        logs.clear();

        String response = mockMvc.perform(post(
                        "/api/v1/communities/{communityId}/memberships/{userId}/invitation/resend",
                        member.community().getId(), member.userId())
                        .header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(tokenCount(member.personalId())).isEqualTo(1);
        assertThat(invitedAt(member)).isEqualTo(invitedAt);
        assertThat(linkWorks(member.token())).isTrue();
        verify(emailSender, never()).send(any());
        assertTheOneWarning();
        assertNoSecretIn(response);
    }

    @Test
    @DisplayName("MEM-011 a revoke works when the server cannot send")
    void aRevoke_works() throws Exception {
        Invited member = invited();
        Instant invitedAt = invitedAt(member);

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/invitation/revoke",
                        member.community().getId(), member.userId())
                        .header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());

        assertThat(linkWorks(member.token())).isFalse();
        assertThat(invitedAt(member)).isEqualTo(invitedAt);
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-012 a bulk send is refused with 503 before anyone is selected, nothing is issued or sent, and "
            + "one warning names only the email category")
    void aBulkSend_isRefused() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        registered(bearer, community.getId(), body);
        String personalId = (String) body.get("personalId");
        logs.clear();

        String response = mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/invite",
                        community.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(tokenCount(personalId)).isZero();
        assertThat(membershipRow(personalId, community.getId()).get("invited_at")).isNull();
        verify(emailSender, never()).send(any());
        assertTheOneWarning();
        assertNoSecretIn(response);
    }

    protected void assertTheOneWarning() {
        assertThat(logs.warningsAndAboveOfThisThread())
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo(expectedWarning()));
    }

    private Instant invitedAt(Invited member) {
        return ((Timestamp) membershipRow(member.personalId(), member.community().getId()).get("invited_at"))
                .toInstant();
    }

    private boolean linkWorks(String token) {
        return getOneTimeTokenService.findMembership(token, OneTimeTokenPurpose.INVITATION).isPresent();
    }
}
