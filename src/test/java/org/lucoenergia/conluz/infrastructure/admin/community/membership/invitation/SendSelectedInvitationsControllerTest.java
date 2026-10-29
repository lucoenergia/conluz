package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sending or resending the invitations of selected members in bulk (MEM-037, #527): each one as the single send or
 * resend would, in a transaction of its own, with one result per selected user, in the order requested.
 */
class SendSelectedInvitationsControllerTest extends BaseInvitationTest {

    /**
     * A member registered in the community, and the contact email of their record, or null if it has none.
     */
    private record Registered(UUID userId, String personalId, String contactEmail) {
    }

    @Test
    @DisplayName("MEM-037 a member never invited is sent, one invited is resent, and one without a contact email is "
            + "refused, and only two emails are sent")
    void neverInvited_invited_andWithoutContactEmail() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Registered neverInvited = register(community, bearer, true);
        InvitedMember invited = invitedMemberOf(community, bearer);
        Registered withoutContactEmail = register(community, bearer, false);

        JsonNode response = sendAndRead(bearer, community.getId(),
                List.of(neverInvited.userId(), invited.userId(), withoutContactEmail.userId()));

        assertThat(response.get("sent").asInt()).isEqualTo(1);
        assertThat(response.get("remaining").asLong()).isZero();
        JsonNode results = response.get("results");
        assertThat(results).hasSize(3);
        assertSent(results.get(0), neverInvited.userId(), "SENT");
        assertSent(results.get(1), invited.userId(), "RESENT");
        assertRefused(results.get(2), withoutContactEmail.userId(), "MEMBERSHIP_WITHOUT_CONTACT_EMAIL",
                Map.of("userId", withoutContactEmail.userId().toString(),
                        "communityId", community.getId().toString()));

        MimeMessage[] emails = awaitNextEmailsHandedOver();
        assertThat(emails).hasSize(2);
        assertThat(linkWorks(tokenFrom(emailTo(emails, neverInvited.contactEmail())))).isTrue();
        assertThat(linkWorks(tokenFrom(emailTo(emails, invited.contactEmail())))).isTrue();
        assertThat(linkWorks(invited.token())).isFalse();
        assertThat(invitationTokenCount(neverInvited.personalId(), community.getId())).isEqualTo(1);
        assertThat(invitationTokenCount(invited.personalId(), community.getId())).isEqualTo(2);
        assertThat(tokenCount(withoutContactEmail.personalId())).isZero();
        assertThat(invitedAt(withoutContactEmail.personalId(), community.getId())).isNull();
    }

    @Test
    @DisplayName("MEM-037 a member over the resend limit is refused with the wait until another resend is allowed, "
            + "and the others are processed")
    void aMemberOverTheResendLimit_isRefusedWithTheWait_andTheOthersProcessed() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        InvitedMember limited = invitedMemberOf(community, bearer);
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofHours(1));
            resend(limited).andExpect(status().isOk());
            tokenFrom(awaitNextEmails(1)[0]);
        }
        clock.advance(Duration.ofHours(1));
        Registered other = register(community, bearer, true);

        JsonNode response = sendAndRead(bearer, community.getId(), List.of(limited.userId(), other.userId()));

        // 20 hours until the first send is 24 hours old, plus the second a send exactly 24 hours old still counts
        assertRefused(response.get("results").get(0), limited.userId(), "MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED",
                Map.of("userId", limited.userId().toString(), "communityId", community.getId().toString(),
                        "retryAfterSeconds", "72001"));
        assertSent(response.get("results").get(1), other.userId(), "SENT");
        MimeMessage[] emails = awaitNextEmailsHandedOver();
        assertThat(emails).hasSize(1);
        tokenFrom(emailTo(emails, other.contactEmail()));
        assertThat(invitationTokenCount(limited.personalId(), community.getId())).isEqualTo(4);
    }

    @Test
    @DisplayName("MEM-037 an unknown user id and the id of another community's member answer the same result, and "
            + "nothing is sent")
    void anUnknownId_andAnotherCommunitysMember_answerTheSameResult() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Community otherCommunity = newCommunity();
        Registered elsewhere = register(otherCommunity, loginAsAdminOf(otherCommunity), true);
        UUID unknown = UUID.randomUUID();

        JsonNode response = sendAndRead(bearer, community.getId(), List.of(unknown, elsewhere.userId()));

        JsonNode results = response.get("results");
        assertRefused(results.get(0), unknown, "MEMBERSHIP_NOT_FOUND",
                Map.of("userId", unknown.toString(), "communityId", community.getId().toString()));
        assertRefused(results.get(1), elsewhere.userId(), "MEMBERSHIP_NOT_FOUND",
                Map.of("userId", elsewhere.userId().toString(), "communityId", community.getId().toString()));
        // Once the requested id is taken out, nothing tells the two apart
        assertThat(withoutUserId(results.get(1), elsewhere.userId())).isEqualTo(withoutUserId(results.get(0), unknown));
        assertThat(response.get("sent").asInt()).isZero();
        verify(emailSender, never()).send(any());
        assertThat(tokenCount(elsewhere.personalId())).isZero();
        assertThat(invitedAt(elsewhere.personalId(), otherCommunity.getId())).isNull();
    }

    @Test
    @DisplayName("MEM-037 an accepted member and a suspended pending member are each refused with their code")
    void anAcceptedMember_andASuspendedMember_areRefused() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        User accepted = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Registered suspended = register(community, bearer, true);
        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/disable", community.getId(),
                        suspended.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        JsonNode results = sendAndRead(bearer, community.getId(), List.of(accepted.getId(), suspended.userId()))
                .get("results");

        assertRefused(results.get(0), accepted.getId(), "MEMBERSHIP_ALREADY_EXISTS",
                Map.of("userId", accepted.getId().toString(), "communityId", community.getId().toString()));
        assertRefused(results.get(1), suspended.userId(), "MEMBERSHIP_DISABLED",
                Map.of("userId", suspended.userId().toString(), "communityId", community.getId().toString()));
        verify(emailSender, never()).send(any());
        assertThat(tokenCount(accepted.getPersonalId())).isZero();
        assertThat(tokenCount(suspended.personalId())).isZero();
    }

    @Test
    @DisplayName("MEM-037 an empty selection, more than 50 user ids, or a user id named twice is refused with its "
            + "code, a missing user id is refused, and nobody is invited")
    void anInvalidSelection_isRefused_andNobodyInvited() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Registered member = register(community, bearer, true);
        List<UUID> fiftyOne = new ArrayList<>(List.of(member.userId()));
        IntStream.range(0, 50).forEach(i -> fiftyOne.add(UUID.randomUUID()));

        assertSelectionRefused(bearer, community.getId(), "{\"userIds\":[]}", "MEMBERSHIP_INVITATION_SELECTION_EMPTY",
                Map.of());
        assertSelectionRefused(bearer, community.getId(), "{}", "MEMBERSHIP_INVITATION_SELECTION_EMPTY", Map.of());
        assertSelectionRefused(bearer, community.getId(), objectMapper.writeValueAsString(Map.of("userIds", fiftyOne)),
                "MEMBERSHIP_INVITATION_SELECTION_TOO_LARGE", Map.of("maxUserIds", "50"));
        assertSelectionRefused(bearer, community.getId(),
                objectMapper.writeValueAsString(Map.of("userIds", List.of(member.userId(), member.userId()))),
                "MEMBERSHIP_INVITATION_SELECTION_DUPLICATED", Map.of());
        // A missing user id is not well formed, so it is refused by the request's validation, without a code
        sendInvitations(bearer, community.getId(), "{\"userIds\":[\"" + member.userId() + "\",null]}")
                .andExpect(status().isBadRequest());

        verify(emailSender, never()).send(any());
        assertThat(tokenCount(member.personalId())).isZero();
        assertThat(invitedAt(member.personalId(), community.getId())).isNull();
    }

    @Test
    @DisplayName("MEM-012 without a body, the members awaiting their first invitation are invited and no result is "
            + "given")
    void withoutABody_theResultsAreEmpty() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Registered member = register(community, bearer, true);

        String response = sendInvitations(bearer, community.getId())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("sent").asInt()).isEqualTo(1);
        assertThat(json.get("remaining").asLong()).isZero();
        assertThat(json.get("results").isArray()).isTrue();
        assertThat(json.get("results")).isEmpty();
        tokenFrom(emailTo(awaitNextEmails(1), member.contactEmail()));
    }

    @Test
    @DisplayName("MEM-037 the selection is processed in ascending user id and answered in the order requested")
    void theSelection_isProcessedInUserIdOrder_andAnsweredInRequestOrder() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        List<UUID> ascending = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ascending.add(register(community, bearer, true).userId());
        }
        ascending.sort(Comparator.comparing(UUID::toString));
        List<UUID> requested = List.of(ascending.get(2), ascending.get(0), ascending.get(3), ascending.get(1));
        clearInvocations(lockUserRepository);

        JsonNode results = sendAndRead(bearer, community.getId(), requested).get("results");

        for (int i = 0; i < requested.size(); i++) {
            assertSent(results.get(i), requested.get(i), "SENT");
        }
        InOrder order = inOrder(lockUserRepository);
        for (UUID userId : ascending) {
            order.verify(lockUserRepository, atLeastOnce()).lock(UserId.of(userId));
        }
        Arrays.stream(awaitNextEmails(4)).forEach(this::rememberToken);
    }

    @Test
    @DisplayName("MEM-037 a refusal in the middle of the selection leaves the memberships before and after it invited")
    void aRefusalInTheMiddle_leavesTheOthersInvited() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        List<Registered> members = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            members.add(register(community, bearer, true));
        }
        members.sort(Comparator.comparing(member -> member.userId().toString()));
        Registered middle = members.get(1);
        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/disable", community.getId(),
                        middle.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        JsonNode results = sendAndRead(bearer, community.getId(),
                members.stream().map(Registered::userId).toList()).get("results");

        assertSent(results.get(0), members.get(0).userId(), "SENT");
        assertRefused(results.get(1), middle.userId(), "MEMBERSHIP_DISABLED",
                Map.of("userId", middle.userId().toString(), "communityId", community.getId().toString()));
        assertSent(results.get(2), members.get(2).userId(), "SENT");
        for (Registered invited : List.of(members.get(0), members.get(2))) {
            assertThat(invitationTokenCount(invited.personalId(), community.getId())).isEqualTo(1);
            assertThat(invitedAt(invited.personalId(), community.getId())).isNotNull();
        }
        assertThat(tokenCount(middle.personalId())).isZero();
        MimeMessage[] emails = awaitNextEmails(2);
        assertThat(Arrays.stream(emails).map(this::recipient))
                .containsExactlyInAnyOrder(members.get(0).contactEmail(), members.get(2).contactEmail());
        Arrays.stream(emails).forEach(this::rememberToken);
    }

    // --- Helpers ---

    private Registered register(Community community, String bearer, boolean withContactEmail) throws Exception {
        Map<String, Object> body = newRecord();
        if (!withContactEmail) {
            body.remove("contactEmail");
        }
        remember((String) body.get("personalId"));
        remember((String) body.get("phoneNumber"));
        UUID userId = registered(bearer, community.getId(), body);
        return new Registered(userId, (String) body.get("personalId"), (String) body.get("contactEmail"));
    }

    private JsonNode sendAndRead(String bearer, UUID communityId, List<UUID> userIds) throws Exception {
        String response = sendInvitations(bearer, communityId, userIds)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoSecretIn(response);
        return objectMapper.readTree(response);
    }

    private void assertSelectionRefused(String bearer, UUID communityId, String body, String code,
                                        Map<String, String> params) throws Exception {
        String response = sendInvitations(bearer, communityId, body)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertNoSecretIn(response);
        JsonNode error = objectMapper.readTree(response).get("errors").get(0);
        assertThat(error.get("code").asText()).isEqualTo(code);
        assertThat(objectMapper.convertValue(error.get("params"), Map.class)).isEqualTo(params);
    }

    private static void assertSent(JsonNode result, UUID userId, String outcome) {
        assertThat(result.get("user").get("id").asText()).isEqualTo(userId.toString());
        assertThat(result.get("outcome").asText()).isEqualTo(outcome);
        assertThat(result.get("code").isNull()).isTrue();
        assertThat(result.get("params")).isEmpty();
    }

    private void assertRefused(JsonNode result, UUID userId, String code, Map<String, String> params) {
        assertThat(result.get("user").get("id").asText()).isEqualTo(userId.toString());
        assertThat(result.get("outcome").asText()).isEqualTo("REFUSED");
        assertThat(result.get("code").asText()).isEqualTo(code);
        assertThat(objectMapper.convertValue(result.get("params"), Map.class)).isEqualTo(params);
    }

    /**
     * The result, with the requested user id replaced by a placeholder wherever it appears.
     */
    private JsonNode withoutUserId(JsonNode result, UUID userId) throws Exception {
        return objectMapper.readTree(result.toString().replace(userId.toString(), "?"));
    }

    private String recipient(MimeMessage email) {
        try {
            return ((InternetAddress) email.getAllRecipients()[0]).getAddress();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void rememberToken(MimeMessage email) {
        try {
            tokenFrom(email);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
