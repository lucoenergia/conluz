package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateOneTimeTokenServiceTest extends BaseOneTimeTokenServiceTest {

    @Test
    void issue_storesOnlyTheHash_andReturnsTheTokenOnce() {
        UserId user = newUser();
        Instant now = clock.instant();

        RawOneTimeToken token = issue(user);

        assertTrue(token.value().matches("^[A-Za-z0-9_-]{43}$"), "32 bytes, URL-safe Base64 without padding");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM one_time_token WHERE user_id = ?", user.getId());
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        String storedHash = (String) row.get("token_hash");
        // assertTrue, not assertEquals: a failure message must not print a token or a hash
        assertTrue(storedHash.matches("^[0-9a-f]{64}$"), "The stored value is not a 64-hex hash");
        assertTrue(sha256(token.value()).equals(storedHash), "The stored value is not the token's SHA-256 hash");
        row.values().forEach(value -> assertFalse(String.valueOf(value).contains(token.value()),
                "A column holds the raw token"));
        assertEquals(PURPOSE.name(), row.get("purpose"));
        assertEquals(now, ((Timestamp) row.get("created_at")).toInstant());
        assertEquals(now.plus(LIFETIME), ((Timestamp) row.get("expires_at")).toInstant());
        assertNull(row.get("used_at"));
        assertNull(row.get("revoked_at"));
        assertFalse(token.toString().contains(token.value().substring(0, 8)), "toString reveals the token");
    }

    @Test
    void issuingAgain_revokesTheEarlierToken() {
        UserId user = newUser();
        RawOneTimeToken first = issue(user);
        clock.advance(Duration.ofMinutes(1));
        Instant secondIssuedAt = clock.instant();

        RawOneTimeToken second = issue(user);

        assertEquals(Optional.empty(), consume(first.value()));
        assertEquals(Optional.of(user.getId()), consume(second.value()));
        assertEquals(secondIssuedAt, jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM one_time_token WHERE token_hash = ?", Timestamp.class,
                sha256(first.value())).toInstant());
    }

    @Test
    void issuingAgain_revokesAnExpiredUnusedToken_too() {
        UserId user = newUser();
        RawOneTimeToken expired = issue(user);
        clock.advance(LIFETIME.plusHours(1));

        issue(user);

        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM one_time_token WHERE token_hash = ?", Timestamp.class,
                sha256(expired.value())));
    }

    @Test
    void issue_forAnUnknownUser_throwsUserNotFound() {
        UserId unknown = UserId.of(UUID.randomUUID());

        assertThrows(UserNotFoundException.class, () -> createService.issue(unknown, PURPOSE));
    }

    @Test
    void concurrentIssuesForTheSameUserAndPurpose_leaveExactlyOneActiveToken() throws Exception {
        UserId user = newUser();
        for (int round = 0; round < ROUNDS; round++) {
            List<RawOneTimeToken> issued = race(() -> issue(user), () -> issue(user));

            assertEquals(1, activeTokenCount(user), "round " + round);
            long consumable = issued.stream()
                    .filter(token -> getService.findOwner(token.value(), PURPOSE).isPresent())
                    .count();
            assertEquals(1, consumable, "round " + round);
        }
    }

    @Test
    void deletingAUser_deletesTheirTokens() {
        UserId user = newUser();
        issue(user);

        deleteUserRepository.delete(user);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM one_time_token WHERE user_id = ?", Integer.class, user.getId()));
    }
}
