package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeOneTimeTokenServiceTest extends BaseOneTimeTokenServiceTest {

    @Test
    void consume_returnsTheUserOnce() {
        UserId user = newUser();
        RawOneTimeToken token = issue(user);

        assertEquals(Optional.of(user.getId()), consume(token.value()));
        assertEquals(Optional.empty(), consume(token.value()));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT used_at FROM one_time_token WHERE user_id = ?", Timestamp.class, user.getId()));
    }

    /**
     * There is a single purpose today, so a token of another purpose is seeded with a purpose name outside the
     * enum. After the refused attempt, the row is relabelled with the requested purpose to show that the attempt
     * left it usable.
     */
    @Test
    void consume_withAnotherPurpose_isRefused_andLeavesTheTokenUsable() {
        UserId user = newUser();
        String raw = randomRawToken();
        Instant now = clock.instant();
        jdbcTemplate.update("INSERT INTO one_time_token (id, user_id, purpose, token_hash, created_at, expires_at) "
                        + "VALUES (?, ?, 'TEST_ONLY_PURPOSE', ?, ?, ?)",
                UUID.randomUUID(), user.getId(), sha256(raw), Timestamp.from(now), Timestamp.from(now.plus(LIFETIME)));

        assertEquals(Optional.empty(), consume(raw));
        assertEquals(Optional.empty(), getService.findOwner(raw, PURPOSE));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT used_at, revoked_at FROM one_time_token WHERE user_id = ?", user.getId());
        assertNull(row.get("used_at"));
        assertNull(row.get("revoked_at"));

        jdbcTemplate.update("UPDATE one_time_token SET purpose = ? WHERE user_id = ?", PURPOSE.name(), user.getId());

        assertEquals(Optional.of(user.getId()), consume(raw));
    }

    @Test
    void consume_oneSecondBeforeExpiry_succeeds() {
        UserId user = newUser();
        RawOneTimeToken token = issue(user);

        clock.advance(LIFETIME.minusSeconds(1));

        assertEquals(Optional.of(user.getId()), consume(token.value()));
    }

    @Test
    void consume_atTheExpiryInstant_isRefused() {
        UserId user = newUser();
        RawOneTimeToken token = issue(user);

        clock.advance(LIFETIME);

        assertEquals(Optional.empty(), consume(token.value()));
    }

    @Test
    void consume_oneSecondAfterExpiry_isRefused() {
        UserId user = newUser();
        RawOneTimeToken token = issue(user);

        clock.advance(LIFETIME.plusSeconds(1));

        assertEquals(Optional.empty(), consume(token.value()));
        assertEquals(Optional.empty(), getService.findOwner(token.value(), PURPOSE));
    }

    @Test
    void concurrentConsumptionsOfTheSameToken_exactlyOneSucceeds() throws Exception {
        UserId user = newUser();
        for (int round = 0; round < ROUNDS; round++) {
            RawOneTimeToken token = issue(user);

            List<Optional<UUID>> outcomes = race(() -> consume(token.value()), () -> consume(token.value()));

            assertEquals(1, outcomes.stream().filter(Optional::isPresent).count(), "round " + round);
        }
    }

    /**
     * The order a flow that consumes a token and then writes its user must follow: find the owner without locking,
     * lock the user row, then consume. Run against a concurrent issue for the same user, which locks the user row and
     * then the token rows, neither may fail with a deadlock.
     */
    @Test
    void issueAndLockUserThenConsume_concurrentlyForTheSameUser_neverDeadlock() throws Exception {
        UserId user = newUser();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (int round = 0; round < ROUNDS; round++) {
            RawOneTimeToken token = issue(user);

            List<Object> outcomes = race(
                    () -> issue(user),
                    () -> transaction.execute(status -> {
                        Optional<UserId> owner = getService.findOwner(token.value(), PURPOSE);
                        if (owner.isEmpty()) {
                            return Optional.empty();
                        }
                        jdbcTemplate.queryForObject("SELECT id FROM users WHERE id = ? FOR NO KEY UPDATE",
                                UUID.class, owner.get().getId());
                        Optional<UserId> consumed = consumeService.consume(token.value(), PURPOSE);
                        if (consumed.isPresent()) {
                            // The issue may have revoked the token first; when it did not, the users must match
                            assertTrue(consumed.equals(owner), "consume returned another user than findOwner");
                            jdbcTemplate.update("UPDATE users SET password = password WHERE id = ?",
                                    owner.get().getId());
                        }
                        return consumed.map(UserId::getId);
                    }));

            assertEquals(2, outcomes.size(), "round " + round);
            assertEquals(1, activeTokenCount(user), "round " + round);
        }
    }

    /**
     * The documented flow compares the user from {@link GetOneTimeTokenService#findOwner} with the one from
     * {@link ConsumeOneTimeTokenService#consume}, which are distinct instances: they must be equal by value.
     */
    @Test
    void findOwnerThenLockUserThenConsume_returnsAnEqualUser() {
        UserId user = newUser();
        RawOneTimeToken token = issue(user);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            Optional<UserId> owner = getService.findOwner(token.value(), PURPOSE);
            assertTrue(owner.isPresent());
            jdbcTemplate.queryForObject("SELECT id FROM users WHERE id = ? FOR NO KEY UPDATE", UUID.class,
                    owner.get().getId());
            Optional<UserId> consumed = consumeService.consume(token.value(), PURPOSE);

            assertTrue(consumed.isPresent());
            assertTrue(consumed.get().equals(owner.get()), "consume returned another user than findOwner");
            assertTrue(consumed.get().equals(user), "consume returned another user than the token's");
            assertEquals(owner.get().hashCode(), consumed.get().hashCode());
        });
    }

    @Test
    void invalidTokens_areRefused_exactlyLikeAnExpiredOne() {
        UserId user = newUser();
        RawOneTimeToken expired = issue(user);
        clock.advance(LIFETIME.plusSeconds(1));
        Optional<UUID> expiredOutcome = consume(expired.value());
        Optional<UserId> expiredOwner = getService.findOwner(expired.value(), PURPOSE);

        List<String> invalid = new ArrayList<>(List.of("", "   ", "%%%", "short", "a".repeat(43), "a".repeat(500),
                randomRawToken()));
        invalid.add(null);
        for (String token : invalid) {
            assertEquals(expiredOutcome, consume(token));
            assertEquals(expiredOwner.map(UserId::getId), getService.findOwner(token, PURPOSE).map(UserId::getId));
        }
        assertEquals(Optional.empty(), expiredOutcome);
    }
}
