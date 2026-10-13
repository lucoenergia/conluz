package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GetOneTimeTokenService#findMembership} and {@link GetOneTimeTokenService#countIssuedForMembershipSince}.
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
}
