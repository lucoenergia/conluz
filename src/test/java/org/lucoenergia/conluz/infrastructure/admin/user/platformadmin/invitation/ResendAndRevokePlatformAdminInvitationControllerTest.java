package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResendAndRevokePlatformAdminInvitationControllerTest extends BasePlatformAdminInvitationTest {

    @Test
    @DisplayName("ACC-019 a resend stops the previous link and code from working, and the new ones work")
    void aResend_replacesTheLinkAndTheCode() throws Exception {
        String bearer = loginAsPlatformAdmin();
        PlatformInvitation first = invited(bearer);
        acceptPlatform(first.token(), newPassword(), wrongCode(first.code()), "192.0.2.180")
                .andExpect(status().isBadRequest());
        assertThat(failedCodeAttempts(first.userId())).isEqualTo(1);

        JsonNode response = json(resendPlatformAdminInvitation(bearer, first.userId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformAdmin.user.id").value(first.userId().toString()))
                .andExpect(jsonPath("$.platformAdmin.invitation.expiresAt")
                        .value(clock.instant().plus(Duration.ofDays(7)).toString())));
        String newCode = codeOf(response);
        String newToken = tokenFrom(nextEmail());

        assertThat(newCode).isNotEqualTo(first.code());
        assertThat(failedCodeAttempts(first.userId())).isZero();
        assertThat(platformLinkWorks(first.token())).isFalse();
        // The old code does not work with the new link, and the old link does not work with the new code
        acceptPlatform(newToken, newPassword(), first.code(), "192.0.2.180")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_INVITATION_CODE_INVALID"));
        acceptPlatform(first.token(), newPassword(), newCode, "192.0.2.180")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));
        acceptPlatform(newToken, newPassword(), newCode, "192.0.2.180").andExpect(status().isNoContent());

        assertThat(isPlatformAdmin(first.userId())).isTrue();
    }

    @Test
    @DisplayName("ACC-019 one send and three resends per day: the next resend is refused with 429 and Retry-After, "
            + "until the first send is a day old")
    void resends_followTheCommunityLimits() throws Exception {
        String bearer = loginAsPlatformAdmin();
        Instant sent = clock.instant();
        PlatformInvitation invitation = invited(bearer);
        for (int resend = 1; resend <= 3; resend++) {
            clock.advance(Duration.ofMinutes(10));
            codeOf(json(resendPlatformAdminInvitation(bearer, invitation.userId()).andExpect(status().isOk())));
            tokenFrom(nextEmail());
        }
        clock.advance(Duration.ofMinutes(10));
        long retryAfter = Duration.between(clock.instant(), sent.plus(Duration.ofHours(24))).getSeconds() + 1;

        String response = resendPlatformAdminInvitation(bearer, invitation.userId())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", String.valueOf(retryAfter)))
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_INVITATION_RESEND_LIMIT_REACHED"))
                .andExpect(jsonPath("$.errors[0].params.retryAfterSeconds").value(String.valueOf(retryAfter)))
                .andReturn().getResponse().getContentAsString();
        assertNoSecretIn(response);

        advanceTo(sent.plus(Duration.ofHours(24)).plusSeconds(1));
        codeOf(json(resendPlatformAdminInvitation(bearer, invitation.userId()).andExpect(status().isOk())));
        tokenFrom(nextEmail());
    }

    @Test
    @DisplayName("ACC-019 a revoke stops the link, and the invitation stays pending and can be sent again")
    void aRevoke_stopsTheLink_andKeepsTheInvitationPending() throws Exception {
        String bearer = loginAsPlatformAdmin();
        PlatformInvitation invitation = invited(bearer);

        revokePlatformAdminInvitation(bearer, invitation.userId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitation.expiresAt").isEmpty())
                .andExpect(jsonPath("$.confirmationCode").doesNotExist());

        assertThat(platformLinkWorks(invitation.token())).isFalse();
        acceptPlatform(invitation.token(), newPassword(), invitation.code(), "192.0.2.181")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));
        JsonNode row = rowOf(platformAdminList(bearer), invitation.userId());
        assertThat(row.get("invitation").get("invitedAt").isNull()).isFalse();
        assertThat(row.get("invitation").get("expiresAt").isNull()).isTrue();
        assertThat(invitationRow(invitation.userId()).get("accepted_at")).isNull();
        assertThat(isPlatformAdmin(invitation.userId())).isFalse();

        resendPlatformAdminInvitation(bearer, invitation.userId()).andExpect(status().isOk())
                .andDo(result -> codeOf(objectMapper.readTree(result.getResponse().getContentAsString())));
        assertThat(platformLinkWorks(tokenFrom(nextEmail()))).isTrue();
    }

    @Test
    @DisplayName("ACC-019 resending or revoking for an unknown user answers 404, and for a user without a pending "
            + "invitation 409")
    void unknownUsersAndUsersWithoutAnInvitation_areRefused() throws Exception {
        String bearer = loginAsPlatformAdmin();
        UUID unknown = UUID.randomUUID();
        User notInvited = newUser();

        resendPlatformAdminInvitation(bearer, unknown).andExpect(status().isNotFound());
        revokePlatformAdminInvitation(bearer, unknown).andExpect(status().isNotFound());
        resendPlatformAdminInvitation(bearer, notInvited.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_NOT_INVITED"));
        revokePlatformAdminInvitation(bearer, notInvited.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_NOT_INVITED"));
    }

    @Test
    @DisplayName("ACC-019 an accepted invitation can no longer be resent or revoked")
    void anAcceptedInvitation_isNoLongerPending() throws Exception {
        String bearer = loginAsPlatformAdmin();
        PlatformInvitation invitation = invited(bearer);
        acceptPlatform(invitation.token(), newPassword(), invitation.code(), "192.0.2.182")
                .andExpect(status().isNoContent());

        resendPlatformAdminInvitation(bearer, invitation.userId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_NOT_INVITED"));
        revokePlatformAdminInvitation(bearer, invitation.userId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_NOT_INVITED"));
    }
}
