package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsumeOneTimeTokenService#consumeForMembership}, and the lock order a flow that consumes an invitation
 * token and then writes its user or membership follows.
 */
class ConsumeOneTimeTokenServiceForMembershipTest extends BaseMembershipOneTimeTokenServiceTest {

    @Test
    @DisplayName("OTT-008 consuming an invitation token returns its membership once")
    void consumeForMembership_returnsTheMembershipOnce() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        RawOneTimeToken token = invite(person, community);

        assertEquals(Optional.of(expected(person, community)), consumeInvitation(token.value()));
        assertEquals(Optional.empty(), consumeInvitation(token.value()));
        assertNotNull(jdbcTemplate.queryForObject("SELECT used_at FROM one_time_token WHERE token_hash = ?",
                Timestamp.class, sha256(token.value())));
    }

    @Test
    @DisplayName("OTT-002 an invitation token is consumed one second before seven days")
    void consumeForMembership_oneSecondBeforeSevenDays_succeeds() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        RawOneTimeToken token = invite(person, community);

        clock.advance(INVITATION_LIFETIME.minusSeconds(1));

        assertEquals(Optional.of(expected(person, community)), consumeInvitation(token.value()));
    }

    @Test
    @DisplayName("OTT-002 an invitation token is refused at seven days")
    void consumeForMembership_atTheExpiryInstant_isRefused() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        RawOneTimeToken token = invite(person, community);

        clock.advance(INVITATION_LIFETIME);

        assertEquals(Optional.empty(), getService.findMembership(token.value(), INVITATION));
        assertEquals(Optional.empty(), consumeInvitation(token.value()));
    }

    @Test
    @DisplayName("OTT-002 an invitation token is refused after seven days")
    void consumeForMembership_oneSecondAfterExpiry_isRefused() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        RawOneTimeToken token = invite(person, community);

        clock.advance(INVITATION_LIFETIME.plusSeconds(1));

        assertEquals(Optional.empty(), getService.findMembership(token.value(), INVITATION));
        assertEquals(Optional.empty(), consumeInvitation(token.value()));
    }

    @Test
    @DisplayName("OTT-008 concurrent consumptions of the same invitation token: exactly one succeeds")
    void concurrentConsumptionsOfTheSameToken_exactlyOneSucceeds() throws Exception {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        for (int round = 0; round < ROUNDS; round++) {
            RawOneTimeToken token = invite(person, community);

            List<Optional<OneTimeTokenMembership>> outcomes = race(() -> consumeInvitation(token.value()),
                    () -> consumeInvitation(token.value()));

            assertEquals(1, outcomes.stream().filter(Optional::isPresent).count(), "round " + round);
        }
    }

    /**
     * The order a flow that consumes an invitation token and then writes its user or membership must follow: find the
     * membership without locking, lock the user row, then the membership row, then consume. Run against a concurrent
     * issue for the same membership, which locks the user row, the membership row and then the token rows, neither
     * may fail with a deadlock.
     */
    @Test
    @DisplayName("OTT-004 issuing and consuming concurrently for the same membership never deadlock")
    void issueAndConsume_concurrentlyForTheSameMembership_neverDeadlock() throws Exception {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (int round = 0; round < ROUNDS; round++) {
            RawOneTimeToken token = invite(person, community);

            List<Object> outcomes = race(
                    () -> invite(person, community),
                    () -> transaction.execute(status -> {
                        Optional<OneTimeTokenMembership> found = getService.findMembership(token.value(), INVITATION);
                        if (found.isEmpty()) {
                            return Optional.empty();
                        }
                        assertTrue(lockUserRepository.lock(found.get().userId()));
                        assertTrue(lockMembershipRepository.lock(found.get().userId(), found.get().communityId()));
                        Optional<OneTimeTokenMembership> consumed = consumeInvitation(token.value());
                        if (consumed.isPresent()) {
                            // The issue may have revoked the token first; when it did not, the memberships must match
                            assertEquals(found, consumed, "consume returned another membership than findMembership");
                            jdbcTemplate.update("UPDATE community_memberships SET accepted_at = now() WHERE id = ?",
                                    found.get().membershipId());
                            jdbcTemplate.update("UPDATE users SET password = password WHERE id = ?",
                                    found.get().userId().getId());
                        }
                        return consumed;
                    }));

            assertEquals(2, outcomes.size(), "round " + round);
            assertEquals(1, activeTokenCount(person, community), "round " + round);
        }
    }

    @Test
    @DisplayName("OTT-008 every invalid invitation token is refused exactly like an expired one")
    void invalidTokens_areRefused_exactlyLikeAnExpiredOne() {
        UserId person = newPerson();
        UUID community = newPendingMembership(person);
        RawOneTimeToken expired = invite(person, community);
        clock.advance(INVITATION_LIFETIME.plusSeconds(1));
        RawOneTimeToken passwordReset = issue(person);
        Optional<OneTimeTokenMembership> expiredOutcome = consumeInvitation(expired.value());

        List<String> invalid = new ArrayList<>(List.of("", "   ", "%%%", "short", "a".repeat(43), "a".repeat(500),
                randomRawToken(), passwordReset.value()));
        invalid.add(null);
        for (String token : invalid) {
            assertEquals(expiredOutcome, consumeInvitation(token));
            assertEquals(expiredOutcome, getService.findMembership(token, INVITATION));
        }
        assertEquals(Optional.empty(), expiredOutcome);
    }
}
