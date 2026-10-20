package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminAccessService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformAdminInvitationAcceptanceTest extends BasePlatformAdminInvitationTest {

    private static final String WRONG_CODE_LOG = "Refused invitation token: ip=%s, reason=WRONG_CONFIRMATION_CODE";

    @Autowired
    private DisableUserRepository disableUserRepository;
    @Autowired
    private ManagePlatformAdminAccessService managePlatformAdminAccessService;

    // --- Inspection ---

    @Test
    @DisplayName("MEM-014 a platform-admin invitation is inspected as PLATFORM, without community, with a code required")
    void aPlatformInvitation_isInspectedAsPlatform() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());

        inspect(invitation.token(), "192.0.2.160")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PLATFORM"))
                .andExpect(jsonPath("$.community").isEmpty())
                .andExpect(jsonPath("$.loginRequired").value(false))
                .andExpect(jsonPath("$.confirmationCodeRequired").value(true));

        assertThat(platformLinkWorks(invitation.token())).isTrue();
    }

    @Test
    @DisplayName("MEM-014 a community invitation is inspected as COMMUNITY, with its community and no code required")
    void aCommunityInvitation_isInspectedAsBefore_withTheNewFields() throws Exception {
        InvitedMember member = invitedMember();

        inspect(member.token(), "192.0.2.161")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("COMMUNITY"))
                .andExpect(jsonPath("$.community.id").value(member.community().getId().toString()))
                .andExpect(jsonPath("$.community.name").value(member.community().getName()))
                .andExpect(jsonPath("$.loginRequired").value(false))
                .andExpect(jsonPath("$.confirmationCodeRequired").value(false));
    }

    // --- The public path ---

    @Test
    @DisplayName("ACC-014 MEM-016 an account without a password accepts with the right code: it gets its password, "
            + "the role, and the invitation's email when it had none")
    void anAccountWithoutAPasswordNorEmail_acceptsWithTheRightCode() throws Exception {
        // A member registered without a contact email: an account with neither a password nor an email
        Community community = newCommunity();
        Map<String, Object> member = newRecord();
        member.remove("contactEmail");
        UUID userId = registered(loginAsAdminOf(community), community.getId(), member);
        String personalId = (String) member.get("personalId");
        assertThat(accountRow(personalId).get("email")).isNull();
        PlatformInvitation invitation = invited(loginAsPlatformAdmin(), invitationBody(personalId));
        assertThat(invitation.userId()).isEqualTo(userId);
        String password = newPassword();

        acceptPlatform(invitation.token(), password, invitation.code(), "192.0.2.162")
                .andExpect(status().isNoContent());

        assertThat(isPlatformAdmin(userId)).isTrue();
        assertThat(accountRow(personalId).get("email")).isEqualTo(invitation.email());
        assertThat(invitationRow(userId).get("accepted_at")).isNotNull();
        assertThat(platformLinkWorks(invitation.token())).isFalse();
        String bearer = bearerOf(login(personalId, password));
        currentUser(bearer).andExpect(jsonPath("$.isPlatformAdmin").value(true));
        assertThat(rowOf(platformAdminList(bearer), userId).get("invitation").isNull()).isTrue();
    }

    @Test
    @DisplayName("ACC-014 MEM-016 an account with an email keeps it when it accepts")
    void anAccountWithAnEmail_keepsIt() throws Exception {
        Community community = newCommunity();
        Map<String, Object> member = newRecord();
        registered(loginAsAdminOf(community), community.getId(), member);
        String personalId = (String) member.get("personalId");
        PlatformInvitation invitation = invited(loginAsPlatformAdmin(), invitationBody(personalId));

        acceptPlatform(invitation.token(), newPassword(), invitation.code(), "192.0.2.163")
                .andExpect(status().isNoContent());

        assertThat(accountRow(personalId).get("email")).isEqualTo(member.get("contactEmail"));
        assertThat(isPlatformAdmin(invitation.userId())).isTrue();
    }

    @Test
    @DisplayName("MEM-016 an account with a password is told to log in on the public path, and the link stays usable")
    void anAccountWithAPassword_isToldToLogIn() throws Exception {
        User existing = newUser();
        remember(existing.getPersonalId());
        PlatformInvitation invitation = invited(loginAsPlatformAdmin(), invitationBody(existing.getPersonalId()));

        acceptPlatform(invitation.token(), newPassword(), invitation.code(), "192.0.2.164")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_INVITATION_LOGIN_REQUIRED"));

        assertThat(isPlatformAdmin(existing.getId())).isFalse();
        assertThat(platformLinkWorks(invitation.token())).isTrue();
        assertThat(failedCodeAttempts(existing.getId())).isZero();
    }

    // --- The authenticated path ---

    @Test
    @DisplayName("ACC-014 MEM-017 an account with a password accepts after logging in, with the right code, and gets "
            + "the role")
    void anAccountWithAPassword_acceptsAfterLoggingIn() throws Exception {
        User existing = newUser();
        remember(existing.getPersonalId());
        PlatformInvitation invitation = invited(loginAsPlatformAdmin(), invitationBody(existing.getPersonalId()));
        String bearer = loginUser(existing);

        acceptPlatformAsUser(bearer, invitation.token(), invitation.code(), "192.0.2.165")
                .andExpect(status().isNoContent());

        assertThat(isPlatformAdmin(existing.getId())).isTrue();
        assertThat(platformLinkWorks(invitation.token())).isFalse();
    }

    @Test
    @DisplayName("MEM-015 MEM-017 another account's platform-admin invitation is refused as an unusable token")
    void anotherAccountsInvitation_isRefused() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());
        String bearer = loginUser(newUser());

        acceptPlatformAsUser(bearer, invitation.token(), invitation.code(), "192.0.2.166")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));

        assertThat(isPlatformAdmin(invitation.userId())).isFalse();
        assertThat(platformLinkWorks(invitation.token())).isTrue();
    }

    // --- Wrong codes ---

    @Test
    @DisplayName("ACC-016 no code and a wrong code are refused, change nothing but the count, which survives the error, "
            + "and the fifth one revokes the link")
    void missingAndWrongCodes_areCounted_andTheFifthRevokesTheLink() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());
        String password = newPassword();
        String clientIp = "192.0.2.167";
        Map<String, Object> accountBefore = accountRow(invitation.personalId());

        for (int attempt = 1; attempt <= 4; attempt++) {
            String code = attempt == 1 ? null : wrongCode(invitation.code());
            String response = acceptPlatform(invitation.token(), password, code, clientIp)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_INVITATION_CODE_INVALID"))
                    .andReturn().getResponse().getContentAsString();

            // Read after the error answer: the count was committed although the request failed
            assertThat(failedCodeAttempts(invitation.userId())).isEqualTo(attempt);
            assertThat(isPlatformAdmin(invitation.userId())).isFalse();
            assertThat(accountRow(invitation.personalId())).isEqualTo(accountBefore);
            assertThat(invitationRow(invitation.userId()).get("accepted_at")).isNull();
            assertThat(platformLinkWorks(invitation.token())).isTrue();
            assertNoSecretIn(response);
        }
        assertThat(logs.allOfThisThread().stream()
                .filter(event -> event.getFormattedMessage().equals(String.format(WRONG_CODE_LOG, clientIp)))
                .count()).isEqualTo(4);

        acceptPlatform(invitation.token(), password, wrongCode(invitation.code()), clientIp)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_INVITATION_CODE_ATTEMPTS_EXHAUSTED"));

        assertThat(failedCodeAttempts(invitation.userId())).isEqualTo(5);
        assertThat(platformLinkWorks(invitation.token())).isFalse();
        // From then on, the link is unusable like any other, even with the right code
        inspect(invitation.token(), clientIp)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));
        acceptPlatform(invitation.token(), password, invitation.code(), clientIp)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));
        assertThat(isPlatformAdmin(invitation.userId())).isFalse();
        // The row stays pending, with no valid link
        JsonNode row = rowOf(platformAdminList(loginAsPlatformAdmin()), invitation.userId());
        assertThat(row.get("invitation").get("expiresAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("ACC-016 a wrong code on the authenticated path is counted the same way")
    void aWrongCodeAfterLoggingIn_isCounted() throws Exception {
        User existing = newUser();
        remember(existing.getPersonalId());
        PlatformInvitation invitation = invited(loginAsPlatformAdmin(), invitationBody(existing.getPersonalId()));

        acceptPlatformAsUser(loginUser(existing), invitation.token(), wrongCode(invitation.code()), "192.0.2.168")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_INVITATION_CODE_INVALID"));

        assertThat(failedCodeAttempts(existing.getId())).isEqualTo(1);
        assertThat(isPlatformAdmin(existing.getId())).isFalse();
    }

    @Test
    @DisplayName("ACC-016 the code is accepted in any case and with spaces or hyphens")
    void theCodeIsAcceptedAsTyped() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());
        String typed = invitation.code().substring(0, 4).toLowerCase() + "-" + invitation.code().substring(4);

        acceptPlatform(invitation.token(), newPassword(), typed, "192.0.2.169").andExpect(status().isNoContent());

        assertThat(isPlatformAdmin(invitation.userId())).isTrue();
    }

    @Test
    @DisplayName("OTT-002 a platform-admin invitation link is accepted until 7 days after it was sent, and refused "
            + "from then on")
    void theLinkIsValidForSevenDays() throws Exception {
        PlatformInvitation stillValid = invited(loginAsPlatformAdmin());
        PlatformInvitation expired = invited(loginAsPlatformAdmin());

        advanceTo(clock.instant().plus(java.time.Duration.ofDays(7)).minusSeconds(1));
        acceptPlatform(stillValid.token(), newPassword(), stillValid.code(), "192.0.2.172")
                .andExpect(status().isNoContent());
        advanceTo(clock.instant().plusSeconds(1));
        acceptPlatform(expired.token(), newPassword(), expired.code(), "192.0.2.172")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));

        assertThat(isPlatformAdmin(stillValid.userId())).isTrue();
        assertThat(isPlatformAdmin(expired.userId())).isFalse();
        assertThat(rowOf(platformAdminList(loginAsPlatformAdmin()), expired.userId()).get("invitation")
                .get("expiresAt").isNull()).isTrue();
    }

    // --- Disabled accounts and counts ---

    @Test
    @DisplayName("ACC-014 MEM-015 a disabled account that accepts gets the role and still cannot log in")
    void aDisabledAccount_getsTheRole_andCannotLogIn() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());
        disableUserRepository.disable(UserId.of(invitation.userId()), clock.instant());
        String password = newPassword();

        inspect(invitation.token(), "192.0.2.170").andExpect(status().isOk());
        acceptPlatform(invitation.token(), password, invitation.code(), "192.0.2.170")
                .andExpect(status().isNoContent());

        assertThat(isPlatformAdmin(invitation.userId())).isTrue();
        login(invitation.personalId(), password).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ACC-013 ACC-014 with one enabled platform admin and one pending invitation, the last-platform-admin "
            + "rule still refuses revoking the enabled one, and the pending row is never counted")
    void aPendingInvitation_isNeverCountedAsAPlatformAdmin() throws Exception {
        // The default admin is the one enabled platform admin
        String bearer = loginAsDefaultPlatformAdmin();
        long before = managePlatformAdminRepository.countPlatformAdmins();
        UUID defaultAdmin = UUID.fromString(currentUserId(bearer));

        PlatformInvitation invitation = invited(bearer);

        assertThat(managePlatformAdminRepository.countPlatformAdmins()).isEqualTo(before).isEqualTo(1);
        assertThatThrownBy(() -> managePlatformAdminAccessService.revoke(UserId.of(defaultAdmin)))
                .isInstanceOf(LastPlatformAdminException.class);
        JsonNode list = platformAdminList(bearer);
        assertThat(rowOf(list, defaultAdmin).get("revocable").asBoolean()).isFalse();
        assertThat(rowOf(list, invitation.userId()).get("revocable").isNull()).isTrue();
        assertThat(isPlatformAdmin(invitation.userId())).isFalse();
    }

    private String currentUserId(String bearer) throws Exception {
        return json(currentUser(bearer).andExpect(status().isOk())).get("id").asText();
    }

    @Test
    @DisplayName("ACC-015 no log line, email or error answer contains the code, the token, the email or the DNI")
    void noSecretLeaks() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());
        remember((String) invitationRow(invitation.userId()).get("confirmation_code_hash"));

        String wrong = acceptPlatform(invitation.token(), newPassword(), wrongCode(invitation.code()), "192.0.2.171")
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertNoSecretIn(wrong);
        acceptPlatform(invitation.token(), newPassword(), invitation.code(), "192.0.2.171")
                .andExpect(status().isNoContent());
        // The log check of every test then asserts that no captured line contains any of them
    }
}
