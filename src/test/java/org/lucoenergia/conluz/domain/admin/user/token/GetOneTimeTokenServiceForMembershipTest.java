package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GetOneTimeTokenService#findMembership}, {@link GetOneTimeTokenService#countIssuedForMembershipSince},
 * {@link GetOneTimeTokenService#findEarliestIssuedForMembershipSince} and the valid expiry of a membership's token.
 */
class GetOneTimeTokenServiceForMembershipTest extends BaseMembershipOneTimeTokenServiceTest {

    @Test
    @DisplayName("OTT-008 finding an invitation token's membership does not consume it")
    void findMembership_returnsTheMembership_withoutConsumingTheToken() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        RawOneTimeToken token = invite(person, community);

        assertEquals(Optional.of(expected(person, community)), getService.findMembership(token.value(), INVITATION));
        assertEquals(Optional.of(expected(person, community)), getService.findMembership(token.value(), INVITATION));
        assertEquals(Optional.of(expected(person, community)), consumeInvitation(token.value()));
        assertEquals(Optional.empty(), getService.findMembership(token.value(), INVITATION));
    }

    @Test
    @DisplayName("OTT-006 the count covers only that membership, exactly at the boundaries, whatever the tokens' state")
    void countIssuedForMembershipSince_coversOnlyThatMembership_atTheBoundaries() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        UUID otherCommunity = newPendingMembership(person);
        UserId otherPerson = newPerson();
        UUID otherPersonsCommunity = newPendingMembership(otherPerson);
        Instant t0 = clock.instant();
        invite(person, community);
        issue(person);
        clock.advance(Duration.ofHours(1));
        RawOneTimeToken second = invite(person, community);
        consumeInvitation(second.value());
        clock.advance(Duration.ofHours(1));
        invite(person, community);
        invite(person, otherCommunity);
        invite(otherPerson, otherPersonsCommunity);
        clock.advance(Duration.ofHours(1));
        Duration microsecond = Duration.ofNanos(1_000);

        assertEquals(3, getService.countIssuedForMembershipSince(person, community, INVITATION, t0.minus(microsecond)));
        assertEquals(3, getService.countIssuedForMembershipSince(person, community, INVITATION, t0));
        assertEquals(2, getService.countIssuedForMembershipSince(person, community, INVITATION, t0.plus(microsecond)));
        assertEquals(2, getService.countIssuedForMembershipSince(person, community, INVITATION,
                t0.plus(Duration.ofHours(1))));
        assertEquals(1, getService.countIssuedForMembershipSince(person, community, INVITATION,
                t0.plus(Duration.ofHours(1)).plus(microsecond)));
        assertEquals(1, getService.countIssuedForMembershipSince(person, community, INVITATION,
                t0.plus(Duration.ofHours(2))));
        assertEquals(0, getService.countIssuedForMembershipSince(person, community, INVITATION,
                t0.plus(Duration.ofHours(2)).plus(microsecond)));
        assertEquals(1, getService.countIssuedForMembershipSince(person, otherCommunity, INVITATION, t0));
        assertEquals(1, getService.countIssuedForMembershipSince(otherPerson, otherPersonsCommunity, INVITATION, t0));
        assertEquals(0, getService.countIssuedForMembershipSince(otherPerson, community, INVITATION, t0));
        assertEquals(1, getService.countIssuedSince(person, PURPOSE, t0));
    }

    @Test
    @DisplayName("OTT-006 counting refuses a window older than the retention")
    void countIssuedForMembershipSince_refusesAWindowLongerThanTheRetention() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        Instant oldestCountable = clock.instant().minus(OneTimeTokenRetention.PERIOD);

        assertDoesNotThrow(() -> getService.countIssuedForMembershipSince(person, community, INVITATION,
                oldestCountable));
        assertThrows(IllegalArgumentException.class, () -> getService.countIssuedForMembershipSince(person, community,
                INVITATION, oldestCountable.minusNanos(1_000)));
    }

    @Test
    @DisplayName("OTT-009 the earliest token is that of the counted ones, only of that membership, at the boundaries")
    void findEarliestIssuedForMembershipSince_isTheOldestCountedToken_ofThatMembershipOnly() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        UUID otherCommunity = newPendingMembership(person);
        clock.advance(Duration.ofMinutes(1));
        invite(person, otherCommunity);
        clock.advance(Duration.ofMinutes(1));
        Instant t0 = clock.instant();
        RawOneTimeToken first = invite(person, community);
        revokeService.revokeForMembership(person, community, INVITATION);
        clock.advance(Duration.ofHours(1));
        Instant t1 = clock.instant();
        RawOneTimeToken second = invite(person, community);
        consumeInvitation(second.value());
        clock.advance(Duration.ofHours(1));
        Duration microsecond = Duration.ofNanos(1_000);

        assertEquals(Optional.of(t0), getService.findEarliestIssuedForMembershipSince(person, community, INVITATION,
                t0.minus(Duration.ofHours(1))));
        assertEquals(Optional.of(t0), getService.findEarliestIssuedForMembershipSince(person, community, INVITATION,
                t0));
        assertEquals(Optional.of(t1), getService.findEarliestIssuedForMembershipSince(person, community, INVITATION,
                t0.plus(microsecond)));
        assertEquals(Optional.empty(), getService.findEarliestIssuedForMembershipSince(person, community, INVITATION,
                t1.plus(microsecond)));
        assertEquals(Optional.empty(), getService.findEarliestIssuedForMembershipSince(newPerson(), community,
                INVITATION, t0));
        assertNotEquals(first.value(), second.value());
    }

    @Test
    @DisplayName("OTT-009 finding the earliest token refuses a window older than the retention")
    void findEarliestIssuedForMembershipSince_refusesAWindowLongerThanTheRetention() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        Instant oldestCountable = clock.instant().minus(OneTimeTokenRetention.PERIOD);

        assertDoesNotThrow(() -> getService.findEarliestIssuedForMembershipSince(person, community, INVITATION,
                oldestCountable));
        assertThrows(IllegalArgumentException.class, () -> getService.findEarliestIssuedForMembershipSince(person,
                community, INVITATION, oldestCountable.minusNanos(1_000)));
    }

    @Test
    @DisplayName("OTT-010 a membership's valid token shows its expiry, until it expires")
    void findValidExpiryForMembership_isTheValidTokensExpiry_untilItExpires() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        assertEquals(Optional.empty(), getService.findValidExpiryForMembership(person, community, INVITATION));

        Instant issuedAt = clock.instant();
        invite(person, community);
        assertEquals(Optional.of(issuedAt.plus(INVITATION_LIFETIME)),
                getService.findValidExpiryForMembership(person, community, INVITATION));

        clock.advance(Duration.ofHours(1));
        Instant reissuedAt = clock.instant();
        invite(person, community);
        assertEquals(Optional.of(reissuedAt.plus(INVITATION_LIFETIME)),
                getService.findValidExpiryForMembership(person, community, INVITATION));

        clock.advance(INVITATION_LIFETIME.minusNanos(1_000));
        assertEquals(Optional.of(reissuedAt.plus(INVITATION_LIFETIME)),
                getService.findValidExpiryForMembership(person, community, INVITATION));
        clock.advance(Duration.ofNanos(1_000));
        assertEquals(Optional.empty(), getService.findValidExpiryForMembership(person, community, INVITATION));
    }

    @Test
    @DisplayName("OTT-010 a used or revoked token has no expiry to show")
    void findValidExpiryForMembership_isEmpty_forAUsedOrRevokedToken() {
        UserId person = newPerson();
        UUID used = newPendingMembership(person);
        UUID revoked = newPendingMembership(person);
        consumeInvitation(invite(person, used).value());
        invite(person, revoked);
        revokeService.revokeForMembership(person, revoked, INVITATION);

        assertEquals(Optional.empty(), getService.findValidExpiryForMembership(person, used, INVITATION));
        assertEquals(Optional.empty(), getService.findValidExpiryForMembership(person, revoked, INVITATION));
    }

    @Test
    @DisplayName("OTT-010 a community's expiries cover each of its memberships with a valid token, and no other")
    void findValidExpiriesInCommunity_coversOnlyTheValidTokensOfThatCommunity() {
        UserId valid = newPerson();
        UUID community = newPendingMembership(valid);
        UserId revoked = newPerson();
        UserId used = newPerson();
        UserId expired = newPerson();
        UserId neverInvited = newPerson();
        for (UserId member : List.of(revoked, used, expired, neverInvited)) {
            createMembershipRepository.create(community, member.getId(), CommunityRole.COMMUNITY_MEMBER);
            MembershipStateTestSupport.markPending(communityMembershipJpaRepository, member.getId(), community);
        }
        UUID otherCommunity = newPendingMembership(valid);
        invite(expired, community);
        clock.advance(Duration.ofDays(1));
        Instant issuedAt = clock.instant();
        invite(valid, community);
        invite(valid, otherCommunity);
        invite(revoked, community);
        revokeService.revokeForMembership(revoked, community, INVITATION);
        consumeInvitation(invite(used, community).value());
        clock.advance(INVITATION_LIFETIME.minus(Duration.ofDays(1)));

        assertEquals(Map.of(membershipId(valid, community), issuedAt.plus(INVITATION_LIFETIME)),
                getService.findValidExpiriesInCommunity(community, INVITATION));
        assertEquals(Map.of(), getService.findValidExpiriesInCommunity(newCommunity(), INVITATION));
    }
}
