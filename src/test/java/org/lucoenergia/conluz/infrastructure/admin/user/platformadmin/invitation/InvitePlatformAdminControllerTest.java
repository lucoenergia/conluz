package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvitePlatformAdminControllerTest extends BasePlatformAdminInvitationTest {

    @Test
    @DisplayName("ACC-014 ACC-015 inviting answers a code, emails the link without the code, and lists the person as "
            + "pending without the role")
    void inviting_answersACode_emailsTheLinkWithoutIt_andListsThePersonAsPending() throws Exception {
        String bearer = loginAsPlatformAdmin();
        Map<String, Object> body = newInvitationBody();

        JsonNode response = json(invitePlatformAdmin(bearer, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformAdmin.fullName").value("Irene Operadora Plataforma"))
                .andExpect(jsonPath("$.platformAdmin.email").value((String) body.get("email")))
                .andExpect(jsonPath("$.platformAdmin.enabled").isEmpty())
                .andExpect(jsonPath("$.platformAdmin.revocable").isEmpty())
                .andExpect(jsonPath("$.platformAdmin.invitation.invitedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.platformAdmin.invitation.expiresAt")
                        .value(clock.instant().plus(java.time.Duration.ofDays(7)).toString()))
                .andExpect(jsonPath("$.platformAdmin.capabilities.canResendInvitation").value(true))
                .andExpect(jsonPath("$.platformAdmin.capabilities.canRevokeInvitation").value(true)));
        String code = codeOf(response);
        UUID userId = UUID.fromString(response.get("platformAdmin").get("user").get("id").asText());

        assertThat(code).hasSize(ConfirmationCode.LENGTH).matches("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{8}");
        MimeMessage email = nextEmail();
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo(body.get("email"));
        String text = bodyOf(email);
        String token = tokenFrom(email);
        assertThat(text).contains("código").doesNotContain(code)
                .doesNotContain((String) body.get("personalId"));
        assertThat(isPlatformAdmin(userId)).isFalse();
        assertThat(platformLinkWorks(token)).isTrue();

        JsonNode row = rowOf(platformAdminList(bearer), userId);
        assertThat(row).isNotNull();
        assertThat(row.get("invitation").isNull()).isFalse();
        assertThat(row.get("enabled").isNull()).isTrue();
        assertNoInvitationSecretIn(platformAdminList(bearer).toString(),
                new PlatformInvitation(userId, (String) body.get("personalId"), (String) body.get("email"), code,
                        token));
        // Only the hash of the code is stored
        assertThat(invitationRow(userId).values()).doesNotContain(code);
    }

    @Test
    @DisplayName("ACC-017 inviting a DNI with an account with a password answers and logs as for a new DNI, and "
            + "never writes the account")
    void invitingAnExistingAccountWithAPassword_isIndistinguishable_andDoesNotWriteIt() throws Exception {
        String bearer = loginAsPlatformAdmin();
        User existing = newUser();
        remember(existing.getPersonalId());

        assertIndistinguishableFromANewDni(bearer, existing.getPersonalId());
    }

    @Test
    @DisplayName("ACC-017 inviting a DNI with an account without a password answers and logs as for a new DNI, and "
            + "never writes the account")
    void invitingAnExistingAccountWithoutAPassword_isIndistinguishable_andDoesNotWriteIt() throws Exception {
        String bearer = loginAsPlatformAdmin();
        Community community = newCommunity();
        Map<String, Object> member = newRecord();
        registered(loginAsAdminOf(community), community.getId(), member);
        String personalId = (String) member.get("personalId");
        remember(personalId);
        assertThat(accountRow(personalId).get("password")).isNull();

        assertIndistinguishableFromANewDni(bearer, personalId);
    }

    private void assertIndistinguishableFromANewDni(String bearer, String existingPersonalId) throws Exception {
        Map<String, Object> accountBefore = accountRow(existingPersonalId);

        logs.clear();
        JsonNode forExisting = json(invitePlatformAdmin(bearer, invitationBody(existingPersonalId))
                .andExpect(status().isOk()));
        codeOf(forExisting);
        List<String> logsForExisting = applicationLogLines();

        logs.clear();
        JsonNode forNew = json(invitePlatformAdmin(bearer, newInvitationBody()).andExpect(status().isOk()));
        codeOf(forNew);
        List<String> logsForNew = applicationLogLines();

        assertThat(comparable(forExisting)).isEqualTo(comparable(forNew));
        assertThat(logsForExisting).isEqualTo(logsForNew).isNotEmpty();
        assertThat(accountRow(existingPersonalId)).isEqualTo(accountBefore);
        awaitNextEmails(2);
    }

    /**
     * The response without what differs between any two invitations: the user id, the email typed and the code.
     */
    private JsonNode comparable(JsonNode response) {
        ObjectNode copy = response.deepCopy();
        copy.remove("confirmationCode");
        ObjectNode row = (ObjectNode) copy.get("platformAdmin");
        row.remove("user");
        row.remove("email");
        return copy;
    }

    /**
     * The application's log lines of this thread, without the ids and the masked personal id that differ between
     * any two invitations.
     */
    private List<String> applicationLogLines() {
        return logs.allOfThisThread().stream()
                .filter(event -> event.getLoggerName().startsWith("org.lucoenergia"))
                .map(ILoggingEvent::getFormattedMessage)
                .map(line -> line.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<id>")
                        .replaceAll("personalId=\\*\\*\\*\\S+", "personalId=<masked>"))
                .toList();
    }

    @Test
    @DisplayName("ACC-018 inviting a person who already holds the role is refused with 409, and nothing is created")
    void invitingAPlatformAdmin_isRefused() throws Exception {
        String bearer = loginAsPlatformAdmin();
        User admin = newPlatformAdmin();
        remember(admin.getPersonalId());

        String response = invitePlatformAdmin(bearer, invitationBody(admin.getPersonalId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("USER_ALREADY_PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.errors[0].params.userId").value(admin.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(platformInvitationCount(admin.getId())).isZero();
        assertThat(tokenCount(admin.getPersonalId())).isZero();
        assertNoSecretIn(response);
    }

    @Test
    @DisplayName("ACC-018 inviting a person with a pending invitation is refused with 409, and the pending one is kept")
    void invitingAPersonWithAPendingInvitation_isRefused() throws Exception {
        String bearer = loginAsPlatformAdmin();
        Map<String, Object> body = newInvitationBody();
        PlatformInvitation pending = invited(bearer, body);

        String response = invitePlatformAdmin(bearer, invitationBody((String) body.get("personalId")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLATFORM_ADMIN_ALREADY_INVITED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(platformInvitationCount(pending.userId())).isEqualTo(1);
        assertThat(platformLinkWorks(pending.token())).isTrue();
        assertNoSecretIn(response);
    }

    @Test
    @DisplayName("ACC-010 ACC-018 an invalid DNI is refused with 400 before any account is looked up")
    void anInvalidPersonalId_isRefused() throws Exception {
        String bearer = loginAsPlatformAdmin();
        Map<String, Object> body = invitationBody("12345678A");

        invitePlatformAdmin(bearer, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PERSONAL_ID_INVALID"));

        assertNotLookedUp("12345678A");
        assertThat(accountCount("12345678A")).isZero();
    }

    @Test
    @DisplayName("ACC-018 a body without a personal id, full name or email is refused with 400")
    void anIncompleteBody_isRefused() throws Exception {
        String bearer = loginAsPlatformAdmin();
        for (String missing : List.of("personalId", "fullName", "email")) {
            Map<String, Object> body = newInvitationBody();
            body.remove(missing);
            invitePlatformAdmin(bearer, body).andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("ACC-018 a community admin, a member and an anonymous caller may not invite, resend or revoke")
    void anyCallerOtherThanAPlatformAdmin_isRefused() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());
        Community community = newCommunity();
        String communityAdmin = loginAsAdminOf(community);
        String member = loginUser(memberOf(community, CommunityRole.COMMUNITY_MEMBER));

        for (String bearer : new String[]{communityAdmin, member}) {
            invitePlatformAdmin(bearer, newInvitationBody()).andExpect(status().isForbidden());
            resendPlatformAdminInvitation(bearer, invitation.userId()).andExpect(status().isForbidden());
            revokePlatformAdminInvitation(bearer, invitation.userId()).andExpect(status().isForbidden());
        }
        invitePlatformAdmin(null, newInvitationBody()).andExpect(status().isUnauthorized());
        resendPlatformAdminInvitation(null, invitation.userId()).andExpect(status().isUnauthorized());
        revokePlatformAdminInvitation(null, invitation.userId()).andExpect(status().isUnauthorized());

        assertThat(platformLinkWorks(invitation.token())).isTrue();
        assertThat(invitationTokenRows(invitation.userId())).isEqualTo(1);
    }

    private int invitationTokenRows(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE user_id = ? "
                + "AND platform_admin_invitation_id IS NOT NULL", Integer.class, userId);
    }

    @Test
    @DisplayName("ACC-015 the invite response is the only one carrying the code: the list, the inspection and the "
            + "email never do")
    void theCodeIsInTheInviteResponseOnly() throws Exception {
        String bearer = loginAsPlatformAdmin();
        PlatformInvitation invitation = invited(bearer);

        String inspection = inspect(invitation.token(), "192.0.2.150")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNoInvitationSecretIn(inspection, invitation);
        assertThat(inspection).doesNotContain(invitation.email());
        assertNoInvitationSecretIn(platformAdminList(bearer).toString(), invitation);
    }

    @Test
    @DisplayName("ACC-014 inviting a person with a random DNI creates an account without a password and the role")
    void invitingANewDni_createsAnAccountWithoutAPasswordNorTheRole() throws Exception {
        String bearer = loginAsPlatformAdmin();
        String personalId = UserMother.randomPersonalId();

        PlatformInvitation invitation = invited(bearer, invitationBody(personalId));

        Map<String, Object> account = accountRow(personalId);
        assertThat(account.get("password")).isNull();
        assertThat(account.get("is_platform_admin")).isEqualTo(false);
        assertThat(account.get("id")).isEqualTo(invitation.userId());
    }
}
