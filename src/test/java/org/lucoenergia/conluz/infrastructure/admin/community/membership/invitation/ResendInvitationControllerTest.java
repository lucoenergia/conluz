package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

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
 * Resending an invitation (MEM-010): a new link, at most 4 sends per membership in any 24 hours.
 */
class ResendInvitationControllerTest extends BaseInvitationTest {

    private static final int RACE_ROUNDS = 5;

    // --- Resending ---

    @Test
    @DisplayName("MEM-010 a resend stops the previous link, sends a working one to the record's current contact "
            + "email, and records its instant")
    void aResend_replacesTheLink_atTheCurrentContactEmail() throws Exception {
        InvitedMember member = invitedMember();
        String correctedEmail = newEmail();
        changeContactEmail(member, correctedEmail);
        clock.advance(Duration.ofHours(1));

        String response = resend(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(member.userId().toString()))
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.invitedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.invitationExpiresAt").value(clock.instant().plus(Duration.ofDays(7))
                        .toString()))
                .andReturn().getResponse().getContentAsString();

        MimeMessage email = awaitNextEmails(1)[0];
        assertThat(email.getAllRecipients()).containsExactly(new InternetAddress(correctedEmail));
        assertThat(email.getSubject()).isEqualTo("Te han invitado a " + member.community().getName() + " en Conluz");
        String token = tokenFrom(email);
        assertThat(linkWorks(token)).isTrue();
        assertThat(linkWorks(member.token())).isFalse();
        assertThat(invitedAt(member.personalId(), member.community().getId())).isEqualTo(clock.instant());
        assertThat(invitationTokenCount(member.personalId(), member.community().getId())).isEqualTo(2);
        assertNoSecretIn(response.replace(correctedEmail, ""));
    }

    @Test
    @DisplayName("MEM-010 a membership whose link expired is sent a new one that works")
    void anExpiredLink_isReplacedByAWorkingOne() throws Exception {
        InvitedMember member = invitedMember();
        clock.advance(Duration.ofDays(7));
        assertThat(linkWorks(member.token())).isFalse();

        resend(member).andExpect(status().isOk());

        String token = tokenFrom(awaitNextEmails(1)[0]);
        assertThat(linkWorks(token)).isTrue();
    }

    @Test
    @DisplayName("MEM-010 one line is logged per resend, with the user, the community and the outcome")
    void oneLineIsLogged() throws Exception {
        InvitedMember member = invitedMember();
        logs.clear();

        resend(member).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member invitation"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("INFO");
                    assertThat(event.getFormattedMessage()).isEqualTo("Member invitation resent: userId="
                            + member.userId() + ", communityId=" + member.community().getId());
                });
        tokenFrom(awaitNextEmails(1)[0]);
    }

    // --- The limit ---

    @Test
    @DisplayName("MEM-010 three resends within 24 hours of the first send are accepted, the fourth is answered 429 "
            + "with the whole seconds until the oldest send is more than 24 hours old")
    void theFourthResendIn24Hours_isRefused_untilTheOldestSendLeavesTheWindow() throws Exception {
        assertTheLimit(Duration.ZERO, 72_001);
    }

    @Test
    @DisplayName("MEM-010 the wait is rounded up when it is not a whole number of seconds")
    void theWait_isRoundedUp() throws Exception {
        assertTheLimit(Duration.ofMillis(500), 72_000);
    }

    /**
     * Invites, resends 3 times an hour apart, and tries again 1 hour later plus {@code offset}: 20 hours minus
     * {@code offset} before the first send is 24 hours old.
     */
    private void assertTheLimit(Duration offset, long expectedRetryAfter) throws Exception {
        InvitedMember member = invitedMember();
        UUID communityId = member.community().getId();
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofHours(1));
            resend(member).andExpect(status().isOk());
            tokenFrom(awaitNextEmails(1)[0]);
        }
        clock.advance(Duration.ofHours(1).plus(offset));
        Instant lastSent = invitedAt(member.personalId(), communityId);
        clearInvocations(emailSender);
        logs.clear();

        MockHttpServletResponse refused = resend(member)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, String.valueOf(expectedRetryAfter)))
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED"))
                .andExpect(jsonPath("$.errors[0].params.retryAfterSeconds").value(String.valueOf(expectedRetryAfter)))
                .andExpect(jsonPath("$.errors[0].params.userId").value(member.userId().toString()))
                .andExpect(jsonPath("$.errors[0].params.communityId").value(communityId.toString()))
                .andReturn().getResponse();
        assertNoSecretIn(refused.getContentAsString());
        assertThat(invitationTokenCount(member.personalId(), communityId)).isEqualTo(4);
        assertThat(invitedAt(member.personalId(), communityId)).isEqualTo(lastSent);
        verify(emailSender, never()).send(any());
        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member invitation"))
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo(
                        "Member invitation resend refused: userId=" + member.userId() + ", communityId="
                                + communityId + ", retryAfterSeconds=" + expectedRetryAfter));

        clock.advance(Duration.ofSeconds(expectedRetryAfter - 1));
        resend(member)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));

        clock.advance(Duration.ofSeconds(1));
        resend(member).andExpect(status().isOk());
        assertThat(linkWorks(tokenFrom(awaitNextEmails(1)[0]))).isTrue();
        assertThat(invitationTokenCount(member.personalId(), communityId)).isEqualTo(5);
    }

    @Test
    @DisplayName("MEM-010 of two concurrent resends at the limit, exactly one is sent")
    void twoConcurrentResendsAtTheLimit_sendExactlyOne() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            InvitedMember member = invitedMember();
            UUID communityId = member.community().getId();
            for (int i = 0; i < 2; i++) {
                clock.advance(Duration.ofMinutes(1));
                resend(member).andExpect(status().isOk());
                tokenFrom(awaitNextEmails(1)[0]);
            }
            clock.advance(Duration.ofMinutes(1));
            Callable<Integer> resend = () -> resend(member).andReturn().getResponse().getStatus();

            List<Object> outcomes = race(List.<Callable<?>>of(resend, resend));

            assertThat(outcomes).as("round %d", round).containsExactlyInAnyOrder(200, 429);
            assertThat(invitationTokenCount(member.personalId(), communityId)).as("round %d", round).isEqualTo(4);
            tokenFrom(awaitNextEmails(1)[0]);
        }
    }

    // --- Refusals ---

    @Test
    @DisplayName("MEM-010 a membership never invited is answered 409, and nothing is issued or sent")
    void aMembershipNeverInvited_isAnswered409() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        UUID userId = registered(bearer, community.getId(), body);

        String response = resend(bearer, community.getId(), userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_NOT_INVITED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(tokenCount(personalId)).isZero();
        assertThat(invitedAt(personalId, community.getId())).isNull();
        verify(emailSender, never()).send(any());
        assertNoSecretIn(response);
    }

    @Test
    @DisplayName("MEM-010 an accepted membership is answered 409 as an existing one, and nothing is issued or sent")
    void anAcceptedMembership_isAnswered409() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        resend(loginAsAdminOf(community), community.getId(), member.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"));

        assertThat(tokenCount(member.getPersonalId())).isZero();
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-010 a member record without a contact email is answered 400, and nothing is issued or sent")
    void aRecordWithoutAContactEmail_isABadRequest() throws Exception {
        InvitedMember member = invitedMember();
        changeContactEmail(member, null);
        Instant invitedAt = invitedAt(member.personalId(), member.community().getId());
        clearInvocations(emailSender);

        resend(member)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_WITHOUT_CONTACT_EMAIL"));

        assertThat(invitationTokenCount(member.personalId(), member.community().getId())).isEqualTo(1);
        assertThat(invitedAt(member.personalId(), member.community().getId())).isEqualTo(invitedAt);
        assertThat(linkWorks(member.token())).isTrue();
        verify(emailSender, never()).send(any());
    }

    @Test
    void aUserWithoutAMembershipHere_isNotFound() throws Exception {
        Community community = newCommunity();
        User elsewhere = memberOf(newCommunity(), CommunityRole.COMMUNITY_MEMBER);

        resend(loginAsAdminOf(community), community.getId(), elsewhere.getId()).andExpect(status().isNotFound());
        resend(loginAsAdminOf(community), community.getId(), UUID.randomUUID()).andExpect(status().isNotFound());

        verify(emailSender, never()).send(any());
    }

    // --- Who may resend ---

    @Test
    @DisplayName("MEM-010 a platform admin who is not a community admin here is forbidden")
    void aPlatformAdmin_isForbidden() throws Exception {
        assertRefused(community -> loginAsPlatformAdmin(), 403);
    }

    @Test
    @DisplayName("MEM-010 a member of the community is forbidden")
    void aMember_isForbidden() throws Exception {
        assertRefused(this::loginAsMemberOf, 403);
    }

    @Test
    @DisplayName("MEM-010 a pending community admin is answered as an outsider")
    void aPendingAdmin_isNotFound() throws Exception {
        assertRefused(this::loginAsPendingAdminOf, 404);
    }

    @Test
    @DisplayName("MEM-010 a disabled community admin is answered as an outsider")
    void aDisabledAdmin_isNotFound() throws Exception {
        assertRefused(this::loginAsDisabledAdminOf, 404);
    }

    @Test
    @DisplayName("MEM-010 a community admin of another community is answered as an outsider")
    void anAdminOfAnotherCommunity_isNotFound() throws Exception {
        assertRefused(community -> loginAsAdminOf(newCommunity()), 404);
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        InvitedMember member = invitedMember();
        clearInvocations(emailSender);

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/invitation/resend",
                        member.community().getId(), member.userId()))
                .andExpect(status().isUnauthorized());

        verify(emailSender, never()).send(any());
    }

    @FunctionalInterface
    private interface Login {
        String as(Community community) throws Exception;
    }

    private void assertRefused(Login login, int status) throws Exception {
        InvitedMember member = invitedMember();
        UUID communityId = member.community().getId();
        Instant invitedAt = invitedAt(member.personalId(), communityId);
        clearInvocations(emailSender);

        resend(login.as(member.community()), communityId, member.userId()).andExpect(status().is(status));

        assertThat(invitationTokenCount(member.personalId(), communityId)).isEqualTo(1);
        assertThat(invitedAt(member.personalId(), communityId)).isEqualTo(invitedAt);
        assertThat(linkWorks(member.token())).isTrue();
        verify(emailSender, never()).send(any());
    }

    /**
     * Corrects the member record's contact email. No operation edits a member record yet, so the entity is changed
     * directly.
     */
    private void changeContactEmail(InvitedMember member, String contactEmail) {
        CommunityMembershipEntity entity = membershipJpaRepository
                .findByUserIdAndCommunityId(member.userId(), member.community().getId()).orElseThrow();
        entity.setContactEmail(contactEmail);
        membershipJpaRepository.saveAndFlush(entity);
    }
}
