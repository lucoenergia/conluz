package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sending first invitations in bulk (MEM-012): at most 50 per request, to the members awaiting acceptance with a
 * contact email who have never been invited.
 */
class SendInvitationsControllerTest extends BaseInvitationTest {

    private static final int RACE_ROUNDS = 3;

    /**
     * A member registered in the community, and the contact email of their record, or null if it has none.
     */
    private record Registered(UUID userId, String personalId, String contactEmail) {
    }

    @Test
    @DisplayName("MEM-012 120 eligible members are invited 50, 50 and 20 at a time, in user id order, the rest are "
            + "skipped, and each response reports what remains")
    void eligibleMembers_areInvitedInBatchesOf50_andTheRestSkipped() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        List<Registered> eligible = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            eligible.add(register(community, bearer, true));
        }
        List<Registered> withoutContactEmail = List.of(register(community, bearer, false),
                register(community, bearer, false));
        List<Registered> disabled = List.of(register(community, bearer, true), register(community, bearer, true));
        disabled.forEach(member -> MembershipStateTestSupport.markDisabled(membershipJpaRepository, member.userId(),
                community.getId()));
        InvitedMember alreadyInvited = invitedMemberOf(community, bearer);
        Instant alreadyInvitedAt = invitedAt(alreadyInvited.personalId(), community.getId());
        User accepted = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        List<UUID> inUserIdOrder = jdbcTemplate.queryForList("SELECT user_id FROM community_memberships "
                + "WHERE community_id = ? AND accepted_at IS NULL AND enabled AND contact_email IS NOT NULL "
                + "AND invited_at IS NULL ORDER BY user_id", UUID.class, community.getId());
        assertThat(inUserIdOrder).hasSize(120);
        Set<String> recipients = new HashSet<>();

        int[][] expected = {{50, 70}, {50, 20}, {20, 0}};
        int invitedSoFar = 0;
        for (int[] call : expected) {
            JsonNode response = sendAndRead(bearer, community.getId());
            assertThat(response.get("sent").asInt()).isEqualTo(call[0]);
            assertThat(response.get("remaining").asLong()).isEqualTo(call[1]);
            // The queue is drained between calls: each call hands over at most 50 emails
            for (MimeMessage email : awaitNextEmails(call[0])) {
                recipients.add(((InternetAddress) email.getAllRecipients()[0]).getAddress());
                tokenFrom(email);
            }
            invitedSoFar += call[0];
            for (UUID userId : inUserIdOrder.subList(0, invitedSoFar)) {
                assertThat(invitationTokenCount(userId, community.getId())).isEqualTo(1);
            }
            for (UUID userId : inUserIdOrder.subList(invitedSoFar, 120)) {
                assertThat(invitationTokenCount(userId, community.getId())).isZero();
            }
        }

        assertThat(recipients).containsExactlyInAnyOrderElementsOf(
                eligible.stream().map(Registered::contactEmail).toList());
        for (Registered skipped : concat(withoutContactEmail, disabled)) {
            assertThat(invitedAt(skipped.personalId(), community.getId())).isNull();
            assertThat(tokenCount(skipped.personalId())).isZero();
        }
        assertThat(invitationTokenCount(alreadyInvited.personalId(), community.getId())).isEqualTo(1);
        assertThat(invitedAt(alreadyInvited.personalId(), community.getId())).isEqualTo(alreadyInvitedAt);
        assertThat(tokenCount(accepted.getPersonalId())).isZero();
        assertThat(logs.all()).noneMatch(event -> event.getFormattedMessage().contains("dropped"));

        JsonNode nothingLeft = sendAndRead(bearer, community.getId());
        assertThat(nothingLeft.get("sent").asInt()).isZero();
        assertThat(nothingLeft.get("remaining").asLong()).isZero();
    }

    @Test
    @DisplayName("MEM-012 a pending, disabled member with a contact email is neither sent nor counted as remaining")
    void aDisabledMember_isNeitherSentNorCounted() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Registered disabled = register(community, bearer, true);
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, disabled.userId(), community.getId());
        Registered eligible = register(community, bearer, true);

        JsonNode response = sendAndRead(bearer, community.getId());

        assertThat(response.get("sent").asInt()).isEqualTo(1);
        assertThat(response.get("remaining").asLong()).isZero();
        assertThat(awaitNextEmails(1)[0].getAllRecipients())
                .containsExactly(new InternetAddress(eligible.contactEmail()));
        assertThat(tokenCount(disabled.personalId())).isZero();
        assertThat(invitedAt(disabled.personalId(), community.getId())).isNull();
    }

    @Test
    @DisplayName("MEM-012 two concurrent bulk sends for the same community never invite a member twice, and both "
            + "complete")
    void twoConcurrentBulkSends_neverInviteAMemberTwice() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            Community community = newCommunity();
            String bearer = loginAsAdminOf(community);
            List<Registered> eligible = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                eligible.add(register(community, bearer, true));
            }
            Callable<MockHttpServletResponse> send = () -> sendInvitations(bearer, community.getId())
                    .andReturn().getResponse();

            List<Object> outcomes = race(List.<Callable<?>>of(send, send));

            int sent = 0;
            for (Object outcome : outcomes) {
                assertThat(outcome).as("round %d", round).isInstanceOf(MockHttpServletResponse.class);
                MockHttpServletResponse response = (MockHttpServletResponse) outcome;
                assertThat(response.getStatus()).as("round %d", round).isEqualTo(200);
                sent += objectMapper.readTree(response.getContentAsString()).get("sent").asInt();
            }
            int invited = 0;
            for (Registered member : eligible) {
                int tokens = invitationTokenCount(member.personalId(), community.getId());
                assertThat(tokens).as("round %d: invitations of one member", round).isLessThanOrEqualTo(1);
                invited += tokens;
            }
            assertThat(invited).as("round %d", round).isEqualTo(sent);
            assertThat(sent).as("round %d", round).isBetween(50, 60);
            for (MimeMessage email : awaitNextEmails(sent)) {
                tokenFrom(email);
            }
        }
    }

    @Test
    @DisplayName("MEM-012 one line is logged per bulk send, with the community and the counts, and no email address")
    void oneLineIsLogged() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        register(community, bearer, true);
        register(community, bearer, false);
        logs.clear();

        sendInvitations(bearer, community.getId()).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member invit"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("INFO");
                    assertThat(event.getFormattedMessage()).isEqualTo("Member invitations sent: communityId="
                            + community.getId() + ", sent=1, skipped=0, remaining=0");
                });
        tokenFrom(awaitNextEmails(1)[0]);
    }

    @Test
    void anUnknownCommunity_isNotFound() throws Exception {
        sendInvitations(loginAsAdminOf(newCommunity()), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // --- Who may send ---

    @Test
    @DisplayName("MEM-012 a platform admin who is not a community admin here is forbidden")
    void aPlatformAdmin_isForbidden() throws Exception {
        assertRefused(community -> loginAsPlatformAdmin(), 403);
    }

    @Test
    @DisplayName("MEM-012 a member of the community is forbidden")
    void aMember_isForbidden() throws Exception {
        assertRefused(this::loginAsMemberOf, 403);
    }

    @Test
    @DisplayName("MEM-012 a pending community admin is answered as an outsider")
    void aPendingAdmin_isNotFound() throws Exception {
        assertRefused(this::loginAsPendingAdminOf, 404);
    }

    @Test
    @DisplayName("MEM-012 a disabled community admin is answered as an outsider")
    void aDisabledAdmin_isNotFound() throws Exception {
        assertRefused(this::loginAsDisabledAdminOf, 404);
    }

    @Test
    @DisplayName("MEM-012 a community admin of another community is answered as an outsider")
    void anAdminOfAnotherCommunity_isNotFound() throws Exception {
        assertRefused(community -> loginAsAdminOf(newCommunity()), 404);
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        Community community = newCommunity();
        Registered member = register(community, loginAsAdminOf(community), true);

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/invite", community.getId()))
                .andExpect(status().isUnauthorized());

        assertThat(tokenCount(member.personalId())).isZero();
    }

    @FunctionalInterface
    private interface Login {
        String as(Community community) throws Exception;
    }

    private void assertRefused(Login login, int status) throws Exception {
        Community community = newCommunity();
        Registered member = register(community, loginAsAdminOf(community), true);

        sendInvitations(login.as(community), community.getId()).andExpect(status().is(status));

        assertThat(tokenCount(member.personalId())).isZero();
        assertThat(invitedAt(member.personalId(), community.getId())).isNull();
        verify(emailSender, never()).send(any());
    }

    private Registered register(Community community, String bearer, boolean withContactEmail) throws Exception {
        Map<String, Object> body = newRecord();
        if (!withContactEmail) {
            body.remove("contactEmail");
        }
        UUID userId = registered(bearer, community.getId(), body);
        return new Registered(userId, (String) body.get("personalId"), (String) body.get("contactEmail"));
    }

    private JsonNode sendAndRead(String bearer, UUID communityId) throws Exception {
        String response = sendInvitations(bearer, communityId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoSecretIn(response);
        return objectMapper.readTree(response);
    }

    private int invitationTokenCount(UUID userId, UUID communityId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token t "
                        + "JOIN community_memberships m ON m.id = t.membership_id "
                        + "WHERE m.user_id = ? AND m.community_id = ? AND t.purpose = 'INVITATION'",
                Integer.class, userId, communityId);
    }

    private static List<Registered> concat(List<Registered> first, List<Registered> second) {
        List<Registered> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
