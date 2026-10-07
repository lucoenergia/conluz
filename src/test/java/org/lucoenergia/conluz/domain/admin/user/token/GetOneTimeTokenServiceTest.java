package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetOneTimeTokenServiceTest extends BaseOneTimeTokenServiceTest {

    @Test
    void findOwner_returnsTheUser_withoutConsumingTheToken() {
        UserId user = newUser();
        RawOneTimeToken token = issue(user);

        assertEquals(Optional.of(user.getId()), getService.findOwner(token.value(), PURPOSE).map(UserId::getId));
        assertEquals(Optional.of(user.getId()), getService.findOwner(token.value(), PURPOSE).map(UserId::getId));
        assertEquals(Optional.of(user.getId()), consume(token.value()));
        assertEquals(Optional.empty(), getService.findOwner(token.value(), PURPOSE));
    }

    @Test
    void findOwner_ofARevokedToken_isEmpty() {
        UserId user = newUser();
        RawOneTimeToken revoked = issue(user);
        issue(user);

        assertEquals(Optional.empty(), getService.findOwner(revoked.value(), PURPOSE));
    }

    @Test
    void countIssuedSince_isExactAtTheBoundaries_whateverTheTokensState() {
        UserId user = newUser();
        UserId otherUser = newUser();
        Instant t0 = clock.instant();
        issue(user);
        clock.advance(Duration.ofHours(1));
        RawOneTimeToken second = issue(user);
        consume(second.value());
        clock.advance(Duration.ofHours(1));
        issue(user);
        issue(otherUser);
        jdbcTemplate.update("INSERT INTO one_time_token (id, user_id, purpose, token_hash, created_at, expires_at) "
                        + "VALUES (?, ?, 'TEST_ONLY_PURPOSE', ?, ?, ?)",
                UUID.randomUUID(), user.getId(), sha256(randomRawToken()), Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant().plus(LIFETIME)));
        clock.advance(Duration.ofHours(1));
        Duration microsecond = Duration.ofNanos(1_000);

        assertEquals(3, getService.countIssuedSince(user, PURPOSE, t0.minus(microsecond)));
        assertEquals(3, getService.countIssuedSince(user, PURPOSE, t0));
        assertEquals(2, getService.countIssuedSince(user, PURPOSE, t0.plus(microsecond)));
        assertEquals(2, getService.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(1))));
        assertEquals(1, getService.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(1)).plus(microsecond)));
        assertEquals(1, getService.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(2))));
        assertEquals(0, getService.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(2)).plus(microsecond)));
        assertEquals(0, getService.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(3))));
        assertEquals(1, getService.countIssuedSince(otherUser, PURPOSE, t0));
    }

    @Test
    void countIssuedSince_refusesAWindowLongerThanTheRetention() {
        UserId user = newUser();
        Instant oldestCountable = clock.instant().minus(OneTimeTokenRetention.PERIOD);

        assertDoesNotThrow(() -> getService.countIssuedSince(user, PURPOSE, oldestCountable));
        assertThrows(IllegalArgumentException.class,
                () -> getService.countIssuedSince(user, PURPOSE, oldestCountable.minusNanos(1_000)));
    }
}
