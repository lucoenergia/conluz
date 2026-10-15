package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Revoking an invitation (MEM-011): the link stops working, and the membership stays pending and can be resent.
 */
class RevokeInvitationControllerTest extends BaseInvitationTest {

    @Test
    @DisplayName("MEM-011 a revoke stops the link, keeps the membership pending with its invitation instant, and a "
            + "resend then sends a working link")
    void aRevoke_stopsTheLink_andTheMembershipCanBeResent() throws Exception {
        InvitedMember member = invitedMember();
        UUID communityId = member.community().getId();
        Instant invitedAt = invitedAt(member.personalId(), communityId);
        clearInvocations(emailSender);
        clock.advance(Duration.ofHours(1));

        String response = revoke(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(member.userId().toString()))
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.invitedAt").value(invitedAt.toString()))
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(linkWorks(member.token())).isFalse();
        assertThat(membershipRow(member.personalId(), communityId).get("accepted_at")).isNull();
        assertThat(invitedAt(member.personalId(), communityId)).isEqualTo(invitedAt);
        verify(emailSender, never()).send(any());
        assertNoSecretIn(response.replace(member.contactEmail(), ""));

        resend(member).andExpect(status().isOk());
        assertThat(linkWorks(tokenFrom(awaitNextEmails(1)[0]))).isTrue();
    }

    @Test
    @DisplayName("MEM-025 the invitation of a disabled membership can still be revoked")
    void aDisabledMembership_canStillBeRevoked() throws Exception {
        InvitedMember member = invitedMember();
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, member.userId(), member.community().getId());

        revoke(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());

        assertThat(linkWorks(member.token())).isFalse();
    }

    @Test
    @DisplayName("MEM-011 a revoke without an active link changes nothing, and is answered the same")
    void aRevokeWithoutAnActiveLink_changesNothing() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        UUID neverInvited = registered(bearer, community.getId(), body);
        Map<String, Object> membershipBefore = membershipRow(personalId, community.getId());

        revoke(bearer, community.getId(), neverInvited)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitedAt").isEmpty())
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());

        assertThat(membershipRow(personalId, community.getId())).isEqualTo(membershipBefore);
        assertThat(tokenCount(personalId)).isZero();

        InvitedMember revoked = invitedMemberOf(community, bearer);
        revoke(revoked).andExpect(status().isOk());
        Map<String, Object> revokedBefore = membershipRow(revoked.personalId(), community.getId());
        List<Map<String, Object>> tokensBefore = tokenRows(revoked.personalId());

        revoke(revoked)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());

        assertThat(membershipRow(revoked.personalId(), community.getId())).isEqualTo(revokedBefore);
        assertThat(tokenRows(revoked.personalId())).isEqualTo(tokensBefore);
    }

    @Test
    @DisplayName("MEM-011 one line is logged per revoke, with the user, the community and whether a link was revoked")
    void oneLineIsLogged() throws Exception {
        InvitedMember member = invitedMember();
        logs.clear();

        revoke(member).andExpect(status().isOk());
        revoke(member).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member invitation"))
                .extracting(event -> event.getLevel() + " " + event.getFormattedMessage())
                .containsExactly(
                        "INFO Member invitation revoked: userId=" + member.userId() + ", communityId="
                                + member.community().getId() + ", revoked=true",
                        "INFO Member invitation revoked: userId=" + member.userId() + ", communityId="
                                + member.community().getId() + ", revoked=false");
    }

    // --- Refusals ---

    @Test
    @DisplayName("MEM-011 an accepted membership is answered 409 as an existing one")
    void anAcceptedMembership_isAnswered409() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        String response = revoke(loginAsAdminOf(community), community.getId(), member.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"))
                .andReturn().getResponse().getContentAsString();

        assertNoSecretIn(response);
    }

    @Test
    void aUserWithoutAMembershipHere_isNotFound() throws Exception {
        Community community = newCommunity();
        User elsewhere = memberOf(newCommunity(), CommunityRole.COMMUNITY_MEMBER);

        revoke(loginAsAdminOf(community), community.getId(), elsewhere.getId()).andExpect(status().isNotFound());
        revoke(loginAsAdminOf(community), community.getId(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // --- Who may revoke ---

    @Test
    @DisplayName("MEM-011 a platform admin who is not a community admin here is forbidden")
    void aPlatformAdmin_isForbidden() throws Exception {
        assertRefused(community -> loginAsPlatformAdmin(), 403);
    }

    @Test
    @DisplayName("MEM-011 a member of the community is forbidden")
    void aMember_isForbidden() throws Exception {
        assertRefused(this::loginAsMemberOf, 403);
    }

    @Test
    @DisplayName("MEM-011 a pending community admin is answered as an outsider")
    void aPendingAdmin_isNotFound() throws Exception {
        assertRefused(this::loginAsPendingAdminOf, 404);
    }

    @Test
    @DisplayName("MEM-011 a disabled community admin is answered as an outsider")
    void aDisabledAdmin_isNotFound() throws Exception {
        assertRefused(this::loginAsDisabledAdminOf, 404);
    }

    @Test
    @DisplayName("MEM-011 a community admin of another community is answered as an outsider")
    void anAdminOfAnotherCommunity_isNotFound() throws Exception {
        assertRefused(community -> loginAsAdminOf(newCommunity()), 404);
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        InvitedMember member = invitedMember();

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/invitation/revoke",
                        member.community().getId(), member.userId()))
                .andExpect(status().isUnauthorized());

        assertThat(linkWorks(member.token())).isTrue();
    }

    @FunctionalInterface
    private interface Login {
        String as(Community community) throws Exception;
    }

    private void assertRefused(Login login, int status) throws Exception {
        InvitedMember member = invitedMember();

        revoke(login.as(member.community()), member.community().getId(), member.userId())
                .andExpect(status().is(status));

        assertThat(linkWorks(member.token())).isTrue();
    }
}
