package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InspectInvitationControllerTest extends BaseInvitationAcceptanceTest {

    @Test
    @DisplayName("MEM-014 inspecting a passwordless account's invitation tells the community and that no login is needed, and writes nothing")
    void aPasswordlessAccount_isToldItsCommunity_andThatNoLoginIsNeeded() throws Exception {
        InvitedMember member = invitedMember();
        Map<String, Object> accountBefore = accountRow(member.personalId());
        Map<String, Object> membershipBefore = membershipRow(member.personalId(), member.community().getId());

        String response = inspect(member.token(), "192.0.2.10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.community.id").value(member.community().getId().toString()))
                .andExpect(jsonPath("$.community.name").value(member.community().getName()))
                .andExpect(jsonPath("$.loginRequired").value(false))
                .andReturn().getResponse().getContentAsString();

        assertNoSecretIn(response);
        assertThat(accountRow(member.personalId())).isEqualTo(accountBefore);
        assertThat(membershipRow(member.personalId(), member.community().getId())).isEqualTo(membershipBefore);
        assertThat(linkWorks(member.token())).isTrue();
        verify(consumeOneTimeTokenService, never()).consumeForMembership(any(), any());
        assertThat(anyThrottleLine()).isFalse();
    }

    @Test
    @DisplayName("MEM-014 inspecting the invitation of an account with a password tells that a login is needed")
    void anAccountWithAPassword_isToldToLogIn() throws Exception {
        InvitedAccount invited = invitedAccountWithPassword();

        inspect(invited.member().token(), "192.0.2.11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.community.id").value(invited.member().community().getId().toString()))
                .andExpect(jsonPath("$.loginRequired").value(true));

        assertThat(linkWorks(invited.member().token())).isTrue();
    }

    @Test
    @DisplayName("MEM-018 inspecting the invitation of a disabled membership is answered like any other")
    void aDisabledMembership_canBeInspected() throws Exception {
        InvitedMember member = invitedMember();
        MembershipStateTestSupport.markDisabled(
                membershipJpaRepository, member.userId(), member.community().getId());

        inspect(member.token(), "192.0.2.12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginRequired").value(false));
    }

    @Test
    void aMissingToken_isABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/invitations/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
