package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
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
 * Inviting a community admin (MEM-020): one operation registers the person with the admin role and emails them the
 * invitation, and reveals nothing about their account.
 */
class InviteCommunityAdminControllerTest extends BaseCommunityAdminTest {

    // --- AC1 ---

    @Test
    @DisplayName("MEM-020 a platform admin's invitation creates a pending admin membership with the record as typed, "
            + "and emails the invitation to its contact email")
    void anInvitation_createsAPendingAdminMembership_andEmailsIt() throws Exception {
        Community community = newCommunity();
        User platformAdmin = newPlatformAdmin();
        Map<String, Object> body = newAdminRecord();
        String personalId = (String) body.get("personalId");

        String response = inviteAdmin(loginUser(platformAdmin), community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalId").value(personalId))
                .andExpect(jsonPath("$.fullName").value(body.get("fullName")))
                .andExpect(jsonPath("$.contactEmail").value(body.get("contactEmail")))
                .andExpect(jsonPath("$.phoneNumber").value(body.get("phoneNumber")))
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.invitedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.invitationExpiresAt").value(clock.instant().plus(Duration.ofDays(7)).toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(fieldNames(objectMapper.readTree(response))).isEqualTo(new java.util.TreeSet<>(ADMIN_FIELDS));

        Map<String, Object> membership = membershipRow(personalId, community.getId());
        assertThat(membership.get("role")).isEqualTo("COMMUNITY_ADMIN");
        assertThat(membership.get("enabled")).isEqualTo(true);
        assertThat(membership.get("accepted_at")).isNull();
        assertThat(membership.get("full_name")).isEqualTo(body.get("fullName"));
        assertThat(membership.get("contact_email")).isEqualTo(body.get("contactEmail"));
        assertThat(membership.get("phone_number")).isEqualTo(body.get("phoneNumber"));
        assertThat(membership.get("member_number")).isNull();
        assertThat(membership.get("address")).isNull();
        assertThat(membership.get("invited_by")).isEqualTo(platformAdmin.getId());
        assertThat(((Timestamp) membership.get("invited_at")).toInstant()).isEqualTo(clock.instant());
        assertThat(accountRow(personalId).get("password")).isNull();

        MimeMessage email = awaitNextEmails(1)[0];
        assertThat(email.getAllRecipients()).containsExactly(new InternetAddress((String) body.get("contactEmail")));
        assertThat(linkWorks(tokenFrom(email))).isTrue();
        assertThat(invitationTokenCount(personalId, community.getId())).isEqualTo(1);
        assertNoSecretIn(response.replace((String) body.get("contactEmail"), ""));
    }

    @Test
    @DisplayName("MEM-020 a personal id is normalised before it is stored")
    void thePersonalId_isNormalised() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newAdminRecord();
        String normalised = (String) body.get("personalId");
        body.put("personalId", " " + normalised.substring(0, 3) + "." + normalised.substring(3).toLowerCase() + "-");

        inviteAdmin(loginAsPlatformAdmin(), community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalId").value(normalised));

        assertThat(accountCount(normalised)).isEqualTo(1);
        awaitNextEmails(1);
    }

    @Test
    @DisplayName("MEM-020 an admin whose account existed elsewhere is answered and logged exactly as a new one, and "
            + "the account is not written")
    void anExistingAccount_isAnsweredAndLoggedAsANewOne() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsPlatformAdmin();
        Community elsewhere = newCommunity();
        User existing = memberOf(elsewhere, CommunityRole.COMMUNITY_MEMBER);
        Map<String, Object> accountBefore = accountRow(existing.getPersonalId());
        Map<String, Object> elsewhereBefore = membershipRow(existing.getPersonalId(), elsewhere.getId());

        Map<String, Object> newBody = newAdminRecord();
        logs.clear();
        String newResponse = inviteAdmin(bearer, community.getId(), newBody)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> newLog = applicationLines((String) newBody.get("personalId"), userIdIn(newResponse));
        awaitNextEmails(1);

        Map<String, Object> existingBody = newAdminRecord();
        existingBody.put("personalId", existing.getPersonalId());
        logs.clear();
        String existingResponse = inviteAdmin(bearer, community.getId(), existingBody)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> existingLog = applicationLines(existing.getPersonalId(), userIdIn(existingResponse));
        awaitNextEmails(1);

        assertThat(withoutIdentity(existingResponse)).isEqualTo(withoutIdentity(newResponse));
        assertThat(existingLog).isEqualTo(newLog).isNotEmpty();
        assertThat(userIdIn(existingResponse)).isEqualTo(existing.getId());
        assertThat(accountRow(existing.getPersonalId())).isEqualTo(accountBefore);
        assertThat(membershipRow(existing.getPersonalId(), elsewhere.getId())).isEqualTo(elsewhereBefore);
        assertThat(membershipRow(existing.getPersonalId(), community.getId()).get("contact_email"))
                .isEqualTo(existingBody.get("contactEmail"));
    }

    @Test
    @DisplayName("MEM-020 one line is logged per invitation, with the user and the community, and no email address "
            + "or unmasked personal id")
    void oneLineIsLogged() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsPlatformAdmin();
        Map<String, Object> body = newAdminRecord();
        String personalId = (String) body.get("personalId");
        logs.clear();

        UUID userId = userIdIn(inviteAdmin(bearer, community.getId(), body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        awaitNextEmails(1);

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Community admin invited"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("INFO");
                    assertThat(event.getFormattedMessage()).isEqualTo("Community admin invited: userId=" + userId
                            + ", communityId=" + community.getId());
                });
        assertThat(logs.all()).noneSatisfy(event ->
                assertThat(event.getFormattedMessage()).contains(personalId));
    }

    // --- Refusals ---

    @Test
    @DisplayName("MEM-020 a person with a membership in the community, in any role or state, is answered 409, and "
            + "nothing is created or sent")
    void anExistingMembership_isAnswered409() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsPlatformAdmin();
        User acceptedMember = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        User acceptedAdmin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        Map<String, Object> pendingBody = newRecord();
        UUID pendingMember = registered(loginAsAdminOf(community), community.getId(), pendingBody);
        User disabledMember = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, disabledMember.getId(), community.getId());
        org.mockito.Mockito.clearInvocations(emailSender);

        for (String personalId : List.of(acceptedMember.getPersonalId(), acceptedAdmin.getPersonalId(),
                (String) pendingBody.get("personalId"), disabledMember.getPersonalId())) {
            Map<String, Object> membershipBefore = membershipRow(personalId, community.getId());
            Map<String, Object> body = newAdminRecord();
            body.put("personalId", personalId);

            String response = inviteAdmin(bearer, community.getId(), body)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(membershipRow(personalId, community.getId())).isEqualTo(membershipBefore);
            assertThat(membershipCount(personalId)).isEqualTo(1);
            assertThat(tokenCount(personalId)).isZero();
            assertNoSecretIn(response);
        }
        assertThat(pendingMember).isNotNull();
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-020 a personal id made only of removed characters is refused with 400, and nothing is created")
    void anEmptyPersonalId_isABadRequest() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsPlatformAdmin();
        for (String personalId : List.of("..-", " . ")) {
            Map<String, Object> body = newAdminRecord();
            body.put("personalId", personalId);

            inviteAdmin(bearer, community.getId(), body).andExpect(status().isBadRequest());

            assertThat(accountCount("")).isZero();
        }
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-020 the full name and the contact email are required, and the contact email must be valid")
    void aMissingRequiredField_isABadRequest() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsPlatformAdmin();
        for (Map.Entry<String, Object> broken : List.<Map.Entry<String, Object>>of(
                Map.entry("contactEmail", ""), Map.entry("contactEmail", "not-an-email"),
                Map.entry("fullName", " "))) {
            Map<String, Object> body = newAdminRecord();
            body.put(broken.getKey(), broken.getValue());

            inviteAdmin(bearer, community.getId(), body).andExpect(status().isBadRequest());

            assertThat(accountCount((String) body.get("personalId"))).isZero();
        }
        Map<String, Object> withoutEmail = newAdminRecord();
        withoutEmail.remove("contactEmail");
        inviteAdmin(bearer, community.getId(), withoutEmail).andExpect(status().isBadRequest());
        assertThat(accountCount((String) withoutEmail.get("personalId"))).isZero();

        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-020 the phone number is optional")
    void thePhoneNumber_isOptional() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newAdminRecord();
        body.remove("phoneNumber");

        inviteAdmin(loginAsPlatformAdmin(), community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").isEmpty());
        awaitNextEmails(1);
    }

    @Test
    void anUnknownCommunity_isNotFound_andNothingIsCreated() throws Exception {
        Map<String, Object> body = newAdminRecord();

        inviteAdmin(loginAsPlatformAdmin(), UUID.randomUUID(), body).andExpect(status().isNotFound());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
        verify(emailSender, never()).send(any());
    }

    // --- AC7: who may invite ---

    @Test
    @DisplayName("MEM-020 an active community admin of the community is forbidden")
    void aCommunityAdmin_isForbidden() throws Exception {
        assertRefused(this::loginAsAdminOf, 403);
    }

    @Test
    @DisplayName("MEM-020 a member of the community is forbidden")
    void aMember_isForbidden() throws Exception {
        assertRefused(this::loginAsMemberOf, 403);
    }

    @Test
    @DisplayName("MEM-020 a community admin is forbidden even for an unknown community")
    void aCommunityAdmin_isForbidden_forAnUnknownCommunity() throws Exception {
        Map<String, Object> body = newAdminRecord();

        inviteAdmin(loginAsAdminOf(newCommunity()), UUID.randomUUID(), body).andExpect(status().isForbidden());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newAdminRecord();

        mockMvc.perform(post("/api/v1/communities/{communityId}/admins/invite", community.getId())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
    }

    // --- Helpers ---

    private interface Login {
        String as(Community community) throws Exception;
    }

    private void assertRefused(Login login, int status) throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newAdminRecord();

        inviteAdmin(login.as(community), community.getId(), body).andExpect(status().is(status));

        assertThat(accountCount((String) body.get("personalId"))).isZero();
        verify(emailSender, never()).send(any());
    }

    private UUID userIdIn(String response) throws Exception {
        return UUID.fromString(objectMapper.readTree(response).get("user").get("id").asText());
    }

    /**
     * The response with what tells two people apart (their ids and contact details) replaced.
     */
    private JsonNode withoutIdentity(String response) throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(response);
        ((ObjectNode) node.get("user")).put("id", "<id>");
        node.put("personalId", "<personalId>");
        node.put("contactEmail", "<contactEmail>");
        return node;
    }

    /**
     * The application's own log lines on this thread, with the person's ids replaced.
     */
    private List<String> applicationLines(String personalId, UUID userId) {
        String normalised = UserPersonalId.normalize(personalId);
        return logs.allOfThisThread().stream()
                .filter(event -> event.getLoggerName().startsWith("org.lucoenergia"))
                .map(ILoggingEvent::getFormattedMessage)
                .map(line -> line.replace(UserPersonalId.mask(normalised), "<personalId>")
                        .replace(userId.toString(), "<userId>"))
                .toList();
    }
}
