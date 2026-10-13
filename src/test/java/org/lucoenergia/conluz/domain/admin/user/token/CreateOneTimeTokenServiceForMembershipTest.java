package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CreateOneTimeTokenService#issueForMembership}, and what deleting a membership or its user does to its tokens.
 */
class CreateOneTimeTokenServiceForMembershipTest extends BaseMembershipOneTimeTokenServiceTest {

    @Test
    @DisplayName("OTT-001 a person invited to two communities holds a valid token for each")
    void issueForMembership_inTwoCommunities_bothStayValidAndConsumable() {
        UserId person = newPerson();
        UUID first = newPendingMembership(person);
        UUID second = newPendingMembership(person);

        RawOneTimeToken firstToken = invite(person, first);
        RawOneTimeToken secondToken = invite(person, second);

        assertEquals(Optional.of(expected(person, first)), getService.findMembership(firstToken.value(), INVITATION));
        assertEquals(Optional.of(expected(person, second)), getService.findMembership(secondToken.value(), INVITATION));
        assertEquals(Optional.of(expected(person, first)), consumeInvitation(firstToken.value()));
        assertEquals(Optional.of(expected(person, second)), consumeInvitation(secondToken.value()));
    }

    @Test
    @DisplayName("OTT-001 issuing again for a membership revokes only that membership's token")
    void issuingAgainForAMembership_revokesOnlyThatMembershipsToken() {
        UserId person = newPerson();
        UUID first = newPendingMembership(person);
        UUID second = newPendingMembership(person);
        RawOneTimeToken replaced = invite(person, first);
        RawOneTimeToken other = invite(person, second);

        RawOneTimeToken replacement = invite(person, first);

        assertEquals(Optional.empty(), getService.findMembership(replaced.value(), INVITATION));
        assertNotNull(jdbcTemplate.queryForObject("SELECT revoked_at FROM one_time_token WHERE token_hash = ?",
                Timestamp.class, sha256(replaced.value())));
        assertEquals(Optional.of(expected(person, first)), getService.findMembership(replacement.value(), INVITATION));
        assertEquals(Optional.of(expected(person, second)), getService.findMembership(other.value(), INVITATION));
        assertEquals(1, activeTokenCount(person, first));
        assertEquals(1, activeTokenCount(person, second));
    }

    @Test
    @DisplayName("OTT-001 a token issued for a membership stores its membership, its user and only its hash")
    void issueForMembership_storesTheMembershipAndUser_andOnlyTheHash() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);

        RawOneTimeToken token = invite(person, community);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT user_id, membership_id, purpose, token_hash, "
                + "created_at, expires_at FROM one_time_token WHERE membership_id = ?", membershipId(person, community));
        assertEquals(person.getId(), row.get("user_id"));
        assertEquals(INVITATION.name(), row.get("purpose"));
        assertEquals(sha256(token.value()), row.get("token_hash"));
        assertEquals(((Timestamp) row.get("created_at")).toInstant().plus(INVITATION_LIFETIME),
                ((Timestamp) row.get("expires_at")).toInstant());
    }

    @Test
    @DisplayName("OTT-001 a password reset token and an invitation token of the same person coexist")
    void passwordResetAndInvitationTokensOfTheSamePerson_coexist() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);

        RawOneTimeToken reset = issue(person);
        RawOneTimeToken invitation = invite(person, community);
        RawOneTimeToken resetAgain = issue(person);

        assertEquals(Optional.empty(), getService.findOwner(reset.value(), PURPOSE));
        assertEquals(Optional.of(person), getService.findOwner(resetAgain.value(), PURPOSE));
        assertEquals(Optional.of(expected(person, community)), getService.findMembership(invitation.value(), INVITATION));
        assertNull(jdbcTemplate.queryForObject("SELECT membership_id FROM one_time_token WHERE token_hash = ?",
                UUID.class, sha256(resetAgain.value())));
    }

    @Test
    @DisplayName("OTT-001 issuing for a membership that does not exist is refused")
    void issueForMembership_withoutTheMembership_throwsMembershipNotFound() {
        UserId person = newPerson();
        UUID community = newCommunity();

        MembershipNotFoundException withoutMembership = assertThrows(MembershipNotFoundException.class,
                () -> createService.issueForMembership(person, community, INVITATION));
        assertEquals(community, withoutMembership.getCommunityId());
        assertEquals(person.getId(), withoutMembership.getUserId());
        assertThrows(MembershipNotFoundException.class,
                () -> createService.issueForMembership(UserId.of(UUID.randomUUID()), community, INVITATION));
        assertEquals(0, tokenCount(person));
    }

    @Test
    @DisplayName("OTT-004 concurrent issues for the same membership leave exactly one active token")
    void concurrentIssuesForTheSameMembership_leaveExactlyOneActiveToken() throws Exception {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        for (int round = 0; round < ROUNDS; round++) {
            List<RawOneTimeToken> issued = race(() -> invite(person, community), () -> invite(person, community));

            assertEquals(1, activeTokenCount(person, community), "round " + round);
            long valid = issued.stream()
                    .filter(token -> getService.findMembership(token.value(), INVITATION).isPresent())
                    .count();
            assertEquals(1, valid, "round " + round);
        }
    }

    @Test
    @DisplayName("OTT-005 deleting a membership deletes its tokens and no other")
    void deletingAMembership_deletesItsTokens() {
        UserId person = newPerson();
        UUID deleted = newPendingMembership(person);
        UUID kept = newPendingMembership(person);
        UUID deletedMembership = membershipId(person, deleted);
        invite(person, deleted);
        invite(person, deleted);
        RawOneTimeToken keptToken = invite(person, kept);
        issue(person);

        deleteMembershipRepository.delete(deleted, person.getId());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE membership_id = ?",
                Integer.class, deletedMembership));
        assertEquals(Optional.of(expected(person, kept)), getService.findMembership(keptToken.value(), INVITATION));
        assertEquals(2, tokenCount(person));
    }

    /**
     * A user with memberships cannot be deleted, so their memberships go first, each with its tokens; deleting the
     * user then deletes the tokens bound only to them.
     */
    @Test
    @DisplayName("OTT-005 deleting a person deletes all their tokens")
    void deletingThePerson_deletesAllTheirTokens() {
        UserId person = newPerson();
        UUID first = newPendingMembership(person);
        UUID second = newPendingMembership(person);
        invite(person, first);
        invite(person, second);
        issue(person);

        deleteMembershipRepository.delete(first, person.getId());
        deleteMembershipRepository.delete(second, person.getId());
        deleteUserRepository.delete(person);

        assertEquals(0, tokenCount(person));
    }
}
