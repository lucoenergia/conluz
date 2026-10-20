package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.accept.BaseInvitationAcceptanceTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inviting a person to the platform-admin role and accepting the invitation (ADR-0004), on the context the community
 * invitation tests share: the embedded SMTP server, the test clock and the log check of secrets. Every personal id,
 * email, token and confirmation code a test sees is registered as a secret, so that no log line may contain it.
 */
public abstract class BasePlatformAdminInvitationTest extends BaseInvitationAcceptanceTest {

    protected static final String PLATFORM_ADMINS = "/api/v1/platform-admins";

    /**
     * A platform-admin invitation as the inviter and the invited person see it: the code from the invite response,
     * and the token from the email.
     */
    protected record PlatformInvitation(UUID userId, String personalId, String email, String code, String token) {
    }

    // --- Fixtures ---

    /**
     * An invitation body with a fresh, valid personal id and destination email.
     */
    protected Map<String, Object> newInvitationBody() {
        return invitationBody(UserMother.randomPersonalId());
    }

    protected Map<String, Object> invitationBody(String personalId) {
        remember(personalId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personalId", personalId);
        body.put("fullName", "Irene Operadora Plataforma");
        body.put("email", newEmail());
        return body;
    }

    /**
     * Invites with {@code body} and waits for the email: the code comes from the response, the token from the link.
     */
    protected PlatformInvitation invited(String bearer, Map<String, Object> body) throws Exception {
        JsonNode response = json(invitePlatformAdmin(bearer, body).andExpect(status().isOk()));
        String code = codeOf(response);
        String token = tokenFrom(awaitNextEmails(1)[0]);
        return new PlatformInvitation(UUID.fromString(response.get("platformAdmin").get("user").get("id").asText()),
                UserPersonalId.normalize((String) body.get("personalId")), (String) body.get("email"), code, token);
    }

    protected PlatformInvitation invited(String bearer) throws Exception {
        return invited(bearer, newInvitationBody());
    }

    protected String codeOf(JsonNode response) {
        String code = response.get("confirmationCode").asText();
        remember(code);
        return code;
    }

    /**
     * A code of the same shape as {@code code} that is not it.
     */
    protected static String wrongCode(String code) {
        char last = code.charAt(code.length() - 1);
        return code.substring(0, code.length() - 1) + (last == 'Z' ? 'Y' : 'Z');
    }

    // --- Requests ---

    protected ResultActions invitePlatformAdmin(String bearer, Map<String, Object> body) throws Exception {
        if (body.get("personalId") instanceof String personalId) {
            deleteAfterwards(UserPersonalId.normalize(personalId));
        }
        var request = post(PLATFORM_ADMINS + "/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions resendPlatformAdminInvitation(String bearer, UUID userId) throws Exception {
        var request = post(PLATFORM_ADMINS + "/{userId}/invitation/resend", userId);
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions revokePlatformAdminInvitation(String bearer, UUID userId) throws Exception {
        var request = post(PLATFORM_ADMINS + "/{userId}/invitation/revoke", userId);
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        return mockMvc.perform(request);
    }

    protected JsonNode platformAdminList(String bearer) throws Exception {
        return json(mockMvc.perform(get(PLATFORM_ADMINS).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()));
    }

    /**
     * The row of {@code userId} in the list, or null if it is not listed.
     */
    protected JsonNode rowOf(JsonNode list, UUID userId) {
        for (JsonNode row : list) {
            if (row.get("user").get("id").asText().equals(userId.toString())) {
                return row;
            }
        }
        return null;
    }

    protected ResultActions acceptPlatform(String token, String newPassword, String code, String clientIp)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        body.put("newPassword", newPassword);
        body.put("confirmationCode", code);
        return mockMvc.perform(post("/api/v1/invitations/accept")
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions acceptPlatformAsUser(String bearer, String token, String code, String clientIp)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        body.put("confirmationCode", code);
        return mockMvc.perform(post("/api/v1/users/current/invitations/accept")
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // --- Emails, links and rows ---

    protected MimeMessage nextEmail() {
        return awaitNextEmails(1)[0];
    }

    /**
     * Whether the platform-admin invitation link would be accepted now, without using it.
     */
    protected boolean platformLinkWorks(String token) {
        return getOneTimeTokenService.findPlatformAdminInvitation(token, OneTimeTokenPurpose.PLATFORM_ADMIN_INVITATION)
                .isPresent();
    }

    protected boolean isPlatformAdmin(UUID userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT is_platform_admin FROM users WHERE id = ?",
                Boolean.class, userId));
    }

    protected Map<String, Object> invitationRow(UUID userId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM platform_admin_invitation WHERE user_id = ? "
                + "ORDER BY invited_at DESC LIMIT 1", userId);
        remember((String) row.get("confirmation_code_hash"));
        return row;
    }

    protected int failedCodeAttempts(UUID userId) {
        return ((Number) invitationRow(userId).get("failed_code_attempts")).intValue();
    }

    protected int platformInvitationCount(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM platform_admin_invitation WHERE user_id = ?",
                Integer.class, userId);
    }

    /**
     * Asserts that the text carries none of the invitation's code, token or personal id, nor their hashes. The email
     * address is left out: the list shows it by design.
     */
    protected void assertNoInvitationSecretIn(String text, PlatformInvitation invitation) {
        org.assertj.core.api.Assertions.assertThat(text)
                .doesNotContain(invitation.code())
                .doesNotContain(invitation.token())
                .doesNotContain(sha256(invitation.token()))
                .doesNotContain(invitation.personalId())
                .doesNotContain((String) invitationRow(invitation.userId()).get("confirmation_code_hash"));
    }
}
