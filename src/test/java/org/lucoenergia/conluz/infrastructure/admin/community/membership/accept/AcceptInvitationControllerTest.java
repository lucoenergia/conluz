package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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

class AcceptInvitationControllerTest extends BaseInvitationAcceptanceTest {

    @Test
    @DisplayName("MEM-016 a passwordless account accepts by setting its first password, then logs in and reaches its community")
    void aPasswordlessAccount_setsItsFirstPassword_becomesActive_andUsesUpTheLink() throws Exception {
        // A password_changed_at taken from the application clock would then lie in the future, and end the session
        // opened below
        moveTheClockAheadOfTheJvmClock();
        InvitedMember member = invitedMember();
        UUID communityId = member.community().getId();
        String password = newPassword();

        inspect(member.token(), "192.0.2.20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginRequired").value(false));

        Instant before = Instant.now();
        MvcResult result = accept(member.token(), password, "192.0.2.20")
                .andExpect(status().isNoContent())
                .andReturn();
        Instant after = Instant.now();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

        Map<String, Object> account = accountRow(member.personalId());
        assertThat(account.get("password")).isNotNull();
        assertThat(account.get("must_change_password")).isEqualTo(false);
        assertThat(((Timestamp) account.get("password_changed_at")).toInstant())
                .as("taken from the JVM clock").isBetween(before.minusMillis(1), after.plusMillis(1));
        assertThat(acceptedAt(member.personalId(), communityId)).isEqualTo(clock.instant());
        assertThat(logs.allOfThisThread()).anyMatch(event -> event.getFormattedMessage().equals(
                "Invitation accepted: userId=" + member.userId() + ", communityId=" + communityId
                        + ", endpoint=public"));

        // The link is used up
        assertThat(linkWorks(member.token())).isFalse();
        accept(member.token(), newPassword(), "192.0.2.21")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));

        // The person can log in, and the membership grants access
        String bearer = bearerOf(login(member.personalId(), password));
        currentUser(bearer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships['" + communityId + "']").value("COMMUNITY_MEMBER"));
        community(bearer, communityId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEM-016 an account with a password is told to log in, and nothing changes")
    void anAccountWithAPassword_isToldToLogIn_andNothingChanges() throws Exception {
        InvitedAccount invited = invitedAccountWithPassword();
        InvitedMember member = invited.member();
        Map<String, Object> accountBefore = accountRow(member.personalId());

        String response = accept(member.token(), newPassword(), "192.0.2.22")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_LOGIN_REQUIRED"))
                .andReturn().getResponse().getContentAsString();

        assertNoSecretIn(response);
        assertThat(accountRow(member.personalId())).isEqualTo(accountBefore);
        assertThat(acceptedAt(member.personalId(), member.community().getId())).isNull();
        assertThat(linkWorks(member.token())).isTrue();
        verify(consumeOneTimeTokenService, never()).consumeForMembership(any(), any());
        assertThat(anyThrottleLine()).as("not counted against the client address").isFalse();
        // The old password still works
        login(invited.user().getPersonalId(), invited.user().getPassword()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEM-016 a new password that breaks the policy changes nothing, and the same link then succeeds")
    void aPolicyViolation_changesNothing_andTheLinkStillWorks() throws Exception {
        InvitedMember member = invitedMember();
        Map<String, Object> accountBefore = accountRow(member.personalId());

        accept(member.token(), "too short", "192.0.2.23")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].params.rule").value("TOO_SHORT"));

        assertThat(accountRow(member.personalId())).isEqualTo(accountBefore);
        assertThat(acceptedAt(member.personalId(), member.community().getId())).isNull();
        assertThat(linkWorks(member.token())).isTrue();
        // The token is never even consumed: a rollback would undo that, but only while the consumption shares the
        // acceptance's transaction
        verify(consumeOneTimeTokenService, never()).consumeForMembership(any(), any());
        assertThat(anyThrottleLine()).as("not counted against the client address").isFalse();

        accept(member.token(), newPassword(), "192.0.2.23").andExpect(status().isNoContent());
        assertThat(acceptedAt(member.personalId(), member.community().getId())).isNotNull();
    }

    @Test
    @DisplayName("ACC-007 a passwordless account without an email takes the accepted record's contact email")
    void anAccountWithoutAnEmail_takesTheContactEmailOfTheAcceptedRecord() throws Exception {
        Community first = newCommunity();
        Map<String, Object> withoutEmail = newRecord();
        withoutEmail.remove("contactEmail");
        String personalId = (String) withoutEmail.get("personalId");
        UUID userId = registered(loginAsAdminOf(first), first.getId(), withoutEmail);
        assertThat(accountRow(personalId).get("email")).isNull();

        Community second = newCommunity();
        String secondAdmin = loginAsAdminOf(second);
        Map<String, Object> withEmail = newRecord();
        withEmail.put("personalId", personalId);
        registered(secondAdmin, second.getId(), withEmail);
        invite(secondAdmin, second.getId(), userId).andExpect(status().isOk());
        String token = tokenFrom(awaitNextEmails(1)[0]);

        accept(token, newPassword(), "192.0.2.24").andExpect(status().isNoContent());

        assertThat(accountRow(personalId).get("email")).isEqualTo(withEmail.get("contactEmail"));
    }

    @Test
    @DisplayName("ACC-007 a passwordless account that has an email keeps it")
    void anAccountWithAnEmail_keepsIt() throws Exception {
        Community first = newCommunity();
        Map<String, Object> firstRecord = newRecord();
        String personalId = (String) firstRecord.get("personalId");
        UUID userId = registered(loginAsAdminOf(first), first.getId(), firstRecord);

        Community second = newCommunity();
        String secondAdmin = loginAsAdminOf(second);
        Map<String, Object> secondRecord = newRecord();
        secondRecord.put("personalId", personalId);
        registered(secondAdmin, second.getId(), secondRecord);
        invite(secondAdmin, second.getId(), userId).andExpect(status().isOk());
        String token = tokenFrom(awaitNextEmails(1)[0]);

        accept(token, newPassword(), "192.0.2.25").andExpect(status().isNoContent());

        assertThat(accountRow(personalId).get("email")).isEqualTo(firstRecord.get("contactEmail"));
    }

    @Test
    @DisplayName("MEM-018 a disabled membership is accepted, and still grants no access")
    void aDisabledMembership_isAccepted_andStillGrantsNoAccess() throws Exception {
        InvitedMember member = invitedMember();
        UUID communityId = member.community().getId();
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, member.userId(), communityId);
        String password = newPassword();

        accept(member.token(), password, "192.0.2.26").andExpect(status().isNoContent());

        assertThat(acceptedAt(member.personalId(), communityId)).isNotNull();
        assertThat(membershipRow(member.personalId(), communityId).get("enabled")).isEqualTo(false);
        String bearer = bearerOf(login(member.personalId(), password));
        currentUser(bearer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships['" + communityId + "']").doesNotExist());
        community(bearer, communityId).andExpect(status().isNotFound());
    }

    @Test
    void aMissingTokenOrPassword_isABadRequest() throws Exception {
        for (String body : new String[]{"{}", "{\"token\": \"x\"}", "{\"newPassword\": \"a long enough password\"}"}) {
            mockMvc.perform(post("/api/v1/invitations/accept")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
