package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevokeOneTimeTokenServiceTest extends BaseMembershipOneTimeTokenServiceTest {

    @Test
    @DisplayName("OTT-007 revoking makes the invitation token unusable and leaves the other membership's token untouched")
    void revokeForMembership_makesTheTokenUnusable_andLeavesTheOtherMembershipsTokenUntouched() {
        UserId person = newPerson();
        UUID revoked = newPendingMembership(person);
        UUID kept = newPendingMembership(person);
        RawOneTimeToken revokedToken = invite(person, revoked);
        RawOneTimeToken keptToken = invite(person, kept);
        RawOneTimeToken reset = issue(person);

        assertTrue(revokeService.revokeForMembership(person, revoked, INVITATION));

        assertEquals(Optional.empty(), getService.findMembership(revokedToken.value(), INVITATION));
        assertEquals(Optional.empty(), consumeInvitation(revokedToken.value()));
        assertEquals(0, activeTokenCount(person, revoked));
        assertEquals(Optional.of(expected(person, kept)), consumeInvitation(keptToken.value()));
        assertEquals(Optional.of(person), getService.findOwner(reset.value(), PURPOSE));
    }

    @Test
    @DisplayName("OTT-007 revoking a membership without an active token changes nothing")
    void revokeForMembership_withNoActiveToken_changesNothing_andReturnsFalse() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        UUID never = newPendingMembership(person);
        RawOneTimeToken used = invite(person, community);
        consumeInvitation(used.value());
        String before = tokenRows(person);

        assertFalse(revokeService.revokeForMembership(person, community, INVITATION));
        assertFalse(revokeService.revokeForMembership(person, never, INVITATION));

        assertEquals(before, tokenRows(person));
    }

    @Test
    @DisplayName("OTT-007 revoking for a membership that does not exist is refused")
    void revokeForMembership_withoutTheMembership_throwsMembershipNotFound() {
        UserId person = newPerson();
        UUID community = newCommunity();

        assertThrows(MembershipNotFoundException.class,
                () -> revokeService.revokeForMembership(person, community, INVITATION));
        assertThrows(MembershipNotFoundException.class,
                () -> revokeService.revokeForMembership(UserId.of(UUID.randomUUID()), community, INVITATION));
    }

    @Test
    @DisplayName("OTT-004 revoking and issuing concurrently for the same membership never deadlock")
    void revokeAndIssue_concurrentlyForTheSameMembership_neverDeadlock() throws Exception {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        for (int round = 0; round < ROUNDS; round++) {
            invite(person, community);

            List<Object> outcomes = race(() -> invite(person, community),
                    () -> revokeService.revokeForMembership(person, community, INVITATION));

            assertEquals(2, outcomes.size(), "round " + round);
            assertTrue(activeTokenCount(person, community) <= 1, "round " + round);
        }
    }

    private String tokenRows(UserId person) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, used_at, revoked_at "
                + "FROM one_time_token WHERE user_id = ? ORDER BY id", person.getId());
        return rows.toString();
    }
}
