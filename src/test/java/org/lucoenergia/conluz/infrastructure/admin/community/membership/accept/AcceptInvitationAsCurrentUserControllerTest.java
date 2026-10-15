package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AcceptInvitationAsCurrentUserControllerTest extends BaseInvitationAcceptanceTest {

    @Test
    @DisplayName("MEM-017 a logged-in account accepts its own invitation, and its password is untouched")
    void aLoggedInAccount_acceptsItsOwnInvitation_andItsPasswordIsUntouched() throws Exception {
        InvitedAccount invited = invitedAccountWithPassword();
        InvitedMember member = invited.member();
        UUID communityId = member.community().getId();
        String bearer = loginUser(invited.user());
        Map<String, Object> accountBefore = accountRow(member.personalId());

        acceptAsUser(bearer, member.token(), "192.0.2.30")
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        assertThat(acceptedAt(member.personalId(), communityId)).isEqualTo(clock.instant());
        Map<String, Object> accountAfter = accountRow(member.personalId());
        assertThat(accountAfter.get("password")).isEqualTo(accountBefore.get("password"));
        assertThat(accountAfter.get("password_changed_at")).isEqualTo(accountBefore.get("password_changed_at"));
        assertThat(accountAfter).isEqualTo(accountBefore);
        assertThat(linkWorks(member.token())).isFalse();
        assertThat(logs.allOfThisThread()).anyMatch(event -> event.getFormattedMessage().equals(
                "Invitation accepted: userId=" + member.userId() + ", communityId=" + communityId
                        + ", endpoint=authenticated"));

        // The session is still valid, and the client sees the new membership when it reads the current user again
        currentUser(bearer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships['" + communityId + "']").value("COMMUNITY_MEMBER"));
        community(bearer, communityId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEM-015 a token of another account's membership is an invalid token, and the other membership is untouched")
    void aTokenOfAnotherAccount_isAnInvalidToken_andTheOtherMembershipIsUntouched() throws Exception {
        InvitedAccount caller = invitedAccountWithPassword();
        InvitedAccount other = invitedAccountWithPassword();
        String bearer = loginUser(caller.user());
        Map<String, Object> otherMembershipBefore = membershipRow(other.member().personalId(),
                other.member().community().getId());

        acceptAsUser(bearer, other.member().token(), "192.0.2.31")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));

        assertThat(membershipRow(other.member().personalId(), other.member().community().getId()))
                .isEqualTo(otherMembershipBefore);
        assertThat(linkWorks(other.member().token())).isTrue();
        assertThat(acceptedAt(caller.member().personalId(), caller.member().community().getId())).isNull();
        verify(consumeOneTimeTokenService, never()).consumeForMembership(any(), any());
        assertThat(refusedTokenWarnings()).isEqualTo(1);
    }

    @Test
    @DisplayName("MEM-018 a disabled membership is accepted by its logged-in account, and still grants no access")
    void aDisabledMembership_isAccepted_andStillGrantsNoAccess() throws Exception {
        InvitedAccount invited = invitedAccountWithPassword();
        UUID communityId = invited.member().community().getId();
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, invited.member().userId(), communityId);
        String bearer = loginUser(invited.user());

        acceptAsUser(bearer, invited.member().token(), "192.0.2.32").andExpect(status().isNoContent());

        assertThat(acceptedAt(invited.member().personalId(), communityId)).isNotNull();
        currentUser(bearer).andExpect(jsonPath("$.memberships['" + communityId + "']").doesNotExist());
        community(bearer, communityId).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MEM-017 a caller who must change their password is refused first, and the link stays usable")
    void aCallerWhoMustChangeTheirPassword_isRefused_andTheLinkStaysUsable() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        User flagged = UserMother.randomUser();
        flagged.enable();
        flagged.requirePasswordChange();
        remember(flagged.getPassword());
        remember(flagged.getEmail());
        createUserRepository.create(flagged);
        InvitedMember member = invitedAccountWithPassword(community, admin, flagged).member();
        String bearer = loginUser(flagged);

        acceptAsUser(bearer, member.token(), "192.0.2.33")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_CHANGE_REQUIRED"));

        assertThat(acceptedAt(member.personalId(), community.getId())).isNull();
        assertThat(linkWorks(member.token())).isTrue();
        assertThat(anyThrottleLine()).isFalse();
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        InvitedAccount invited = invitedAccountWithPassword();

        mockMvc.perform(post("/api/v1/users/current/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", invited.member().token()))))
                .andExpect(status().isUnauthorized());

        assertThat(linkWorks(invited.member().token())).isTrue();
    }
}
