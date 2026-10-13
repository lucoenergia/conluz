package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.token.OneTimeTokenCleanupTask;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cleanup deletes finished invitation tokens after the retention, exactly as it does the other tokens.
 * Scheduling is off in tests, so the job is called directly.
 */
class InvitationTokenCleanupTest extends BaseMembershipOneTimeTokenServiceTest {

    @Autowired
    private OneTimeTokenCleanupTask task;

    /**
     * With the cleanup at T0+15d the cutoff is T0+8d.
     */
    @Test
    void deletesInvitationTokensFinishedBeforeTheRetention_andKeepsTheOthers() {
        UserId person = newPerson();
        UUID expiredMembership = newPendingMembership(person);
        UUID revokedMembership = newPendingMembership(person);
        UUID usedMembership = newPendingMembership(person);
        UUID expiringAfterCutoffMembership = newPendingMembership(person);
        UUID activeMembership = newPendingMembership(person);

        // T0: expires at T0+7d
        RawOneTimeToken expired = invite(person, expiredMembership);
        // T0+1d: revoked and used then; T0+1d+1h: expires an hour after the cutoff
        clock.advance(Duration.ofDays(1));
        RawOneTimeToken revoked = invite(person, revokedMembership);
        assertTrue(revokeService.revokeForMembership(person, revokedMembership, INVITATION));
        RawOneTimeToken used = invite(person, usedMembership);
        assertTrue(consumeInvitation(used.value()).isPresent());
        clock.advance(Duration.ofHours(1));
        RawOneTimeToken expiringAfterCutoff = invite(person, expiringAfterCutoffMembership);
        // T0+8d
        clock.advance(Duration.ofDays(7).minusHours(1));
        RawOneTimeToken active = invite(person, activeMembership);
        // T0+15d
        clock.advance(Duration.ofDays(7));

        task.cleanup();

        assertFalse(stored(expired), "expired before the retention");
        assertFalse(stored(revoked), "revoked before the retention");
        assertFalse(stored(used), "used before the retention");
        assertTrue(stored(expiringAfterCutoff), "expired within the retention");
        assertTrue(stored(active), "active");
    }

    private boolean stored(RawOneTimeToken token) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE token_hash = ?", Integer.class,
                sha256(token.value())) == 1;
    }
}
