package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseSendingMemberTest;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SendInvitationControllerTest extends BaseSendingMemberTest {

    private static final Pattern LINK = Pattern.compile(
            Pattern.quote(PUBLIC_WEB_URL + "/accept-invitation#") + "([A-Za-z0-9_-]+)");

    // --- Sending ---

    @Test
    @DisplayName("MEM-009 a registered member is sent one invitation at the record's contact email, with the token "
            + "only in the fragment of the link")
    void aRegisteredMember_isSentOneInvitation() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        UUID userId = registered(bearer, community.getId(), body);

        String response = invite(bearer, community.getId(), userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.personalId").value(personalId))
                .andExpect(jsonPath("$.contactEmail").value(body.get("contactEmail")))
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.invitedAt").value(clock.instant().toString()))
                .andReturn().getResponse().getContentAsString();

        MimeMessage email = awaitEmails(1)[0];
        assertThat(email.getAllRecipients()).containsExactly(new InternetAddress((String) body.get("contactEmail")));
        assertThat(email.getSubject()).isEqualTo("Te han invitado a " + community.getName() + " en Conluz");
        String text = bodyOf(email);
        assertThat(text).contains(community.getName());
        assertThat(text).contains("El enlace es válido durante 7 días");
        String token = tokenFrom(text);
        // The token appears once, after the '#': never in the path or a query
        assertThat(text.indexOf(token)).isEqualTo(text.indexOf(PUBLIC_WEB_URL + "/accept-invitation#")
                + (PUBLIC_WEB_URL + "/accept-invitation#").length());
        assertThat(text.indexOf(token)).isEqualTo(text.lastIndexOf(token));
        assertThat(text).doesNotContain("?");

        assertThat(((Timestamp) membershipRow(personalId, community.getId()).get("invited_at")).toInstant())
                .isEqualTo(clock.instant());
        List<Map<String, Object>> tokens = tokenRows(personalId);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).get("purpose")).isEqualTo("INVITATION");
        assertThat(tokens.get(0).get("token_hash")).isEqualTo(sha256(token));
        assertThat(tokens.get(0).get("membership_id"))
                .isEqualTo(membershipRow(personalId, community.getId()).get("id"));
        assertThat(((Timestamp) tokens.get(0).get("expires_at")).toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofDays(7)));
        // The response carries neither the token nor its hash
        assertNoSecretIn(response.replace((String) body.get("contactEmail"), ""));
    }

    @Test
    @DisplayName("MEM-009 a member whose account existed elsewhere is invited at the record's contact email, never "
            + "the account's email")
    void aMemberWithAnAccountElsewhere_isInvitedAtTheContactEmail() throws Exception {
        Community community = newCommunity();
        User existing = memberOf(newCommunity(), CommunityRole.COMMUNITY_MEMBER);
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        body.put("personalId", existing.getPersonalId());
        UUID userId = registered(bearer, community.getId(), body);
        Map<String, Object> accountBefore = accountRow(existing.getPersonalId());

        invite(bearer, community.getId(), userId).andExpect(status().isOk());

        MimeMessage email = awaitEmails(1)[0];
        assertThat(email.getAllRecipients()).containsExactly(new InternetAddress((String) body.get("contactEmail")));
        assertThat(bodyOf(email)).doesNotContain(existing.getEmail());
        tokenFrom(bodyOf(email));
        assertThat(accountRow(existing.getPersonalId())).isEqualTo(accountBefore);
    }

    @Test
    @DisplayName("MEM-009 the response carries exactly the membership, its state and the record, and no account "
            + "field but its id")
    void theResponse_hasExactlyTheMembershipFields() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        UUID userId = registered(bearer, community.getId(), newRecord());

        String response = invite(bearer, community.getId(), userId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(fieldNames(json)).isEqualTo(new TreeSet<>(Set.of("id", "user", "community", "role", "personalId",
                "fullName", "memberNumber", "contactEmail", "address", "phoneNumber", "acceptedAt", "enabled",
                "invitedAt", "invitationExpiresAt", "capabilities")));
        assertThat(fieldNames(json.get("user"))).isEqualTo(new TreeSet<>(Set.of("id")));
        assertThat(json.get("capabilities").get("canInvite").asBoolean()).isTrue();
        tokenFrom(bodyOf(awaitEmails(1)[0]));
    }

    @Test
    @DisplayName("MEM-009 one line is logged per invitation, with the user and the community, and no email address")
    void oneLineIsLogged() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        UUID userId = registered(bearer, community.getId(), newRecord());
        logs.clear();

        invite(bearer, community.getId(), userId).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member invited"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("INFO");
                    assertThat(event.getFormattedMessage()).isEqualTo("Member invited: userId=" + userId
                            + ", communityId=" + community.getId());
                });
        tokenFrom(bodyOf(awaitEmails(1)[0]));
    }

    // --- Refusals ---

    @Test
    @DisplayName("MEM-009 a membership already invited is answered 409, and nothing more is issued or sent")
    void anInvitedMembership_isAnswered409() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        UUID userId = registered(bearer, community.getId(), body);
        invite(bearer, community.getId(), userId).andExpect(status().isOk());
        tokenFrom(bodyOf(awaitEmails(1)[0]));
        Map<String, Object> membershipBefore = membershipRow(personalId, community.getId());
        List<Map<String, Object>> tokensBefore = tokenRows(personalId);
        org.mockito.Mockito.clearInvocations(emailSender);

        String response = invite(bearer, community.getId(), userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_INVITED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(membershipRow(personalId, community.getId())).isEqualTo(membershipBefore);
        assertThat(tokenRows(personalId)).isEqualTo(tokensBefore);
        verify(emailSender, never()).send(any());
        assertNoSecretIn(response);
    }

    @Test
    @DisplayName("MEM-009 an accepted membership is answered 409 as an existing one, and nothing is issued or sent")
    void anAcceptedMembership_isAnswered409() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        invite(loginAsAdminOf(community), community.getId(), member.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"));

        assertThat(tokenCount(member.getPersonalId())).isZero();
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-009 a member record without a contact email is answered 400, and nothing is issued or sent")
    void aRecordWithoutAContactEmail_isABadRequest() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        body.remove("contactEmail");
        String personalId = (String) body.get("personalId");
        UUID userId = registered(bearer, community.getId(), body);

        invite(bearer, community.getId(), userId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_WITHOUT_CONTACT_EMAIL"));

        assertThat(tokenCount(personalId)).isZero();
        assertThat(membershipRow(personalId, community.getId()).get("invited_at")).isNull();
        verify(emailSender, never()).send(any());
    }

    @Test
    void aUserWithoutAMembershipHere_isNotFound() throws Exception {
        Community community = newCommunity();
        User elsewhere = memberOf(newCommunity(), CommunityRole.COMMUNITY_MEMBER);

        invite(loginAsAdminOf(community), community.getId(), elsewhere.getId()).andExpect(status().isNotFound());
        invite(loginAsAdminOf(community), community.getId(), UUID.randomUUID()).andExpect(status().isNotFound());

        verify(emailSender, never()).send(any());
    }

    @Test
    void anUnknownCommunity_isNotFound() throws Exception {
        invite(loginAsAdminOf(newCommunity()), UUID.randomUUID(), UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    // --- Who may invite ---

    @Test
    @DisplayName("MEM-009 a platform admin who is not a community admin here is forbidden")
    void aPlatformAdmin_isForbidden() throws Exception {
        assertRefused(community -> loginAsPlatformAdmin(), 403);
    }

    @Test
    @DisplayName("MEM-009 a plain member of the community is forbidden")
    void aMemberOfTheCommunity_isForbidden() throws Exception {
        assertRefused(this::loginAsMemberOf, 403);
    }

    @Test
    @DisplayName("MEM-009 a pending community admin is answered as a non-member")
    void aPendingAdmin_isAnsweredAsANonMember() throws Exception {
        assertRefused(this::loginAsPendingAdminOf, 404);
    }

    @Test
    @DisplayName("MEM-009 a disabled community admin is answered as a non-member")
    void aDisabledAdmin_isAnsweredAsANonMember() throws Exception {
        assertRefused(this::loginAsDisabledAdminOf, 404);
    }

    @Test
    @DisplayName("MEM-009 an active admin of another community is answered as a non-member")
    void anAdminOfAnotherCommunity_isAnsweredAsANonMember() throws Exception {
        assertRefused(community -> loginAsAdminOf(newCommunity()), 404);
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        UUID userId = registered(loginAsAdminOf(community), community.getId(), body);

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/invite",
                        community.getId(), userId))
                .andExpect(status().isUnauthorized());

        assertThat(tokenCount((String) body.get("personalId"))).isZero();
    }

    // --- Helpers ---

    private interface Login {
        String as(Community community) throws Exception;
    }

    /**
     * Registers a member with a contact email, then has {@code login} invite them: the request is answered
     * {@code status}, and nothing is issued or sent.
     */
    private void assertRefused(Login login, int status) throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        UUID userId = registered(loginAsAdminOf(community), community.getId(), body);

        invite(login.as(community), community.getId(), userId).andExpect(status().is(status));

        assertThat(tokenCount((String) body.get("personalId"))).isZero();
        assertThat(membershipRow((String) body.get("personalId"), community.getId()).get("invited_at")).isNull();
        verify(emailSender, never()).send(any());
    }

    private String tokenFrom(String text) {
        Matcher matcher = LINK.matcher(text);
        assertThat(matcher.find()).as("the email contains an invitation link").isTrue();
        String token = matcher.group(1);
        remember(token);
        return token;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
