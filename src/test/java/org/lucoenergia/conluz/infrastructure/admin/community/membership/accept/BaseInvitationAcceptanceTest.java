package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.BaseInvitationTest;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.AuthenticationThrottleServiceImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inspecting and accepting invitations (#394), against the real database and an embedded SMTP server. Every link is
 * taken from the email the member was sent.
 */
public abstract class BaseInvitationAcceptanceTest extends BaseInvitationTest {

    protected static final String REFUSED_TOKEN_LOG = "Refused invitation token: ip=%s, reason=INVALID_TOKEN";

    /**
     * A pending, invited member whose account already had a password before it was registered, and that account,
     * with its raw password, ready to log in.
     */
    protected record InvitedAccount(InvitedMember member, User user) {
    }

    // --- Fixtures ---

    /**
     * Registers an existing account with a password as a member of a new community, and invites it. Registration
     * never writes an existing account, so the account keeps its password and email.
     */
    protected InvitedAccount invitedAccountWithPassword() throws Exception {
        Community community = newCommunity();
        return invitedAccountWithPassword(community, loginAsAdminOf(community), newUser());
    }

    protected InvitedAccount invitedAccountWithPassword(Community community, String bearer, User user)
            throws Exception {
        Map<String, Object> body = newRecord();
        body.put("personalId", user.getPersonalId());
        UUID userId = registered(bearer, community.getId(), body);
        assertThat(userId).isEqualTo(user.getId());
        invite(bearer, community.getId(), userId).andExpect(status().isOk());
        String token = tokenFrom(awaitNextEmails(1)[0]);
        return new InvitedAccount(new InvitedMember(community, bearer, userId, user.getPersonalId(),
                (String) body.get("contactEmail"), token), user);
    }

    protected String newPassword() {
        String password = "first-password-" + UUID.randomUUID();
        remember(password);
        return password;
    }

    // --- Requests ---

    protected ResultActions inspect(String token, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/inspect")
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    protected ResultActions accept(String token, String newPassword, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/accept")
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
    }

    protected ResultActions acceptAsUser(String bearer, String token, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/users/current/invitations/accept")
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    protected ResultActions login(String personalId, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", personalId, "password", password))));
    }

    protected String bearerOf(ResultActions login) throws Exception {
        String body = login.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    protected ResultActions currentUser(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/users/current").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    protected ResultActions community(String bearer, UUID communityId) throws Exception {
        return mockMvc.perform(get("/api/v1/communities/{communityId}", communityId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    // --- Rows and logs ---

    protected Instant acceptedAt(String personalId, UUID communityId) {
        Timestamp acceptedAt = (Timestamp) membershipRow(personalId, communityId).get("accepted_at");
        return acceptedAt == null ? null : acceptedAt.toInstant();
    }

    /**
     * The warnings the throttling logged on this thread for unusable invitation tokens.
     */
    protected long refusedTokenWarnings() {
        return logs.allOfThisThread().stream()
                .filter(event -> event.getLoggerName().equals(AuthenticationThrottleServiceImpl.class.getName()))
                .filter(event -> event.getFormattedMessage().startsWith("Refused invitation token"))
                .count();
    }

    protected boolean anyThrottleLine() {
        return logs.allOfThisThread().stream()
                .anyMatch(event -> event.getLoggerName().equals(AuthenticationThrottleServiceImpl.class.getName()));
    }
}
