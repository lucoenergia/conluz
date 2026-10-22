package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * When a membership was last invited and when its invitation link expires, as every response that shows a
 * membership reports them.
 */
class MembershipInvitationExpiryControllerTest extends BaseInvitationTest {

    private static final Duration LINK_LIFETIME = Duration.ofDays(7);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("MEM-013 registering shows no link, and inviting shows when the link sent expires")
    void registerShowsNone_andInviteShowsTheLinksExpiry() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        UUID userId = UUID.fromString(objectMapper.readTree(register(bearer, community.getId(), newRecord())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.invitationExpiresAt").isEmpty())
                        .andReturn().getResponse().getContentAsString())
                .get("user").get("id").asText());

        invite(bearer, community.getId(), userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationExpiresAt").value(clock.instant().plus(LINK_LIFETIME).toString()));
        tokenFrom(awaitNextEmails(1)[0]);
    }

    @Test
    @DisplayName("MEM-013 the roster shows each membership's link expiry, and null without a link or once it expired")
    void theRoster_showsEachLinksExpiry() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        InvitedMember expiring = invitedMemberOf(community, bearer);
        clock.advance(Duration.ofDays(1));
        Instant invitedAt = clock.instant();
        InvitedMember invited = invitedMemberOf(community, bearer);
        UUID notInvited = registered(bearer, community.getId(), newRecord());
        User accepted = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        JsonNode roster = roster(bearer, community.getId());
        assertThat(expiryIn(roster, expiring.userId())).isEqualTo(invitedAt.minus(Duration.ofDays(1))
                .plus(LINK_LIFETIME).toString());
        assertThat(expiryIn(roster, invited.userId())).isEqualTo(invitedAt.plus(LINK_LIFETIME).toString());
        assertThat(expiryIn(roster, notInvited)).isNull();
        assertThat(expiryIn(roster, accepted.getId())).isNull();

        clock.advance(LINK_LIFETIME.minus(Duration.ofDays(1)));
        roster = roster(bearer, community.getId());
        assertThat(expiryIn(roster, expiring.userId())).isNull();
        assertThat(expiryIn(roster, invited.userId())).isEqualTo(invitedAt.plus(LINK_LIFETIME).toString());
    }

    @Test
    @DisplayName("MEM-013 changing a pending membership's role shows its link's expiry")
    void updatingTheRole_showsTheLinksExpiry() throws Exception {
        InvitedMember member = invitedMember();
        Instant expiry = clock.instant().plus(LINK_LIFETIME);

        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}",
                        member.community().getId(), member.userId())
                        .header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"COMMUNITY_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationExpiresAt").value(expiry.toString()));
    }

    @Test
    @DisplayName("MEM-013 the roster reads every invitation and link expiry in one query, whatever the number of "
            + "invited members")
    void theRoster_readsTheExpiriesInOneQuery() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        invitedMemberOf(community, bearer);
        long forOne = statementsForRoster(bearer, community.getId());

        for (int i = 0; i < 4; i++) {
            invitedMemberOf(community, bearer);
        }
        long forFive = statementsForRoster(bearer, community.getId());

        assertThat(forFive).as("the roster must not query once per invited member").isEqualTo(forOne);
    }

    @Test
    @DisplayName("MEM-013 the roster shows a membership never invited with no invitation and no link")
    void theRoster_showsANeverInvitedMembershipWithNoInvitation() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        UUID notInvited = registered(bearer, community.getId(), newRecord());

        JsonNode roster = roster(bearer, community.getId());
        assertThat(invitedAtIn(roster, notInvited)).isNull();
        assertThat(expiryIn(roster, notInvited)).isNull();
    }

    @Test
    @DisplayName("MEM-013 the roster shows when an invited membership was invited and when its link expires")
    void theRoster_showsWhenAnInvitedMembershipWasInvited() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Instant invitedAt = clock.instant();
        InvitedMember invited = invitedMemberOf(community, bearer);

        JsonNode roster = roster(bearer, community.getId());
        assertThat(invitedAtIn(roster, invited.userId())).isEqualTo(invitedAt.toString());
        assertThat(expiryIn(roster, invited.userId())).isEqualTo(invitedAt.plus(LINK_LIFETIME).toString());
    }

    @Test
    @DisplayName("MEM-013 the roster keeps showing when a membership was invited once its link expired")
    void theRoster_keepsTheInvitationOnceTheLinkExpired() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Instant invitedAt = clock.instant();
        InvitedMember invited = invitedMemberOf(community, bearer);
        clock.advance(LINK_LIFETIME);

        JsonNode roster = roster(bearer, community.getId());
        assertThat(invitedAtIn(roster, invited.userId())).isEqualTo(invitedAt.toString());
        assertThat(expiryIn(roster, invited.userId())).isNull();
    }

    @Test
    @DisplayName("MEM-013 the roster keeps showing when a membership was invited once its link was revoked")
    void theRoster_keepsTheInvitationOnceTheLinkWasRevoked() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Instant invitedAt = clock.instant();
        InvitedMember invited = invitedMemberOf(community, bearer);
        revoke(invited).andExpect(status().isOk());

        JsonNode roster = roster(bearer, community.getId());
        assertThat(invitedAtIn(roster, invited.userId())).isEqualTo(invitedAt.toString());
        assertThat(expiryIn(roster, invited.userId())).isNull();
    }

    @Test
    @DisplayName("MEM-013 after a resend the roster shows the resend as when the membership was last invited")
    void theRoster_showsTheResendAsTheLastInvitation() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        InvitedMember invited = invitedMemberOf(community, bearer);
        clock.advance(Duration.ofDays(1));
        Instant resentAt = clock.instant();
        resend(invited).andExpect(status().isOk());
        awaitNextEmails(1);

        JsonNode roster = roster(bearer, community.getId());
        assertThat(invitedAtIn(roster, invited.userId())).isEqualTo(resentAt.toString());
        assertThat(expiryIn(roster, invited.userId())).isEqualTo(resentAt.plus(LINK_LIFETIME).toString());
    }

    @Test
    @DisplayName("MEM-013 changing a pending membership's role shows when it was invited")
    void updatingTheRole_showsWhenItWasInvited() throws Exception {
        Instant invitedAt = clock.instant();
        InvitedMember member = invitedMember();

        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}",
                        member.community().getId(), member.userId())
                        .header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"COMMUNITY_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitedAt").value(invitedAt.toString()));
    }

    private JsonNode roster(String bearer, UUID communityId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/communities/{communityId}/memberships", communityId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static String expiryIn(JsonNode roster, UUID userId) {
        for (JsonNode row : roster) {
            if (userId.toString().equals(row.get("user").get("id").asText())) {
                assertThat(row.has("invitationExpiresAt")).isTrue();
                JsonNode expiry = row.get("invitationExpiresAt");
                return expiry.isNull() ? null : expiry.asText();
            }
        }
        throw new AssertionError("The roster has no membership of " + userId);
    }

    private static String invitedAtIn(JsonNode roster, UUID userId) {
        for (JsonNode row : roster) {
            if (userId.toString().equals(row.get("user").get("id").asText())) {
                assertThat(row.has("invitedAt")).isTrue();
                JsonNode invitedAt = row.get("invitedAt");
                return invitedAt.isNull() ? null : invitedAt.asText();
            }
        }
        throw new AssertionError("The roster has no membership of " + userId);
    }

    private long statementsForRoster(String bearer, UUID communityId) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        roster(bearer, communityId);
        return statistics.getPrepareStatementCount();
    }
}
