package org.lucoenergia.conluz.domain.admin.user.token;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against the real database and commits, since concurrent callers must see each other's rows. Every test
 * creates its own users and deletes them afterwards, which deletes their tokens too.
 */
class OneTimeTokenServiceTest extends BaseIntegrationTest {

    private static final OneTimeTokenPurpose PURPOSE = OneTimeTokenPurpose.PASSWORD_RESET;
    private static final Duration LIFETIME = Duration.ofDays(1);
    private static final int ROUNDS = 10;

    @Autowired
    private OneTimeTokenService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeleteUserRepository deleteUserRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> users = new ArrayList<>();
    private final List<String> secrets = new CopyOnWriteArrayList<>();
    private LogCapture logs;

    @BeforeEach
    void captureLogs() {
        logs = LogCapture.ofRoot();
    }

    @AfterEach
    void noLogLineContainsATokenOrItsHash_andDeleteTheUsers() {
        try {
            for (ILoggingEvent event : logs.all()) {
                String line = event.getFormattedMessage()
                        + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy()));
                for (String secret : secrets) {
                    assertFalse(line.contains(secret), "A log line contains a one-time token or its hash");
                }
            }
        } finally {
            logs.close();
            users.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
        }
    }

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
        assertEquals(Optional.empty(), service.findOwner(raw, PURPOSE));
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
        assertEquals(Optional.empty(), service.findOwner(token.value(), PURPOSE));
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

        assertThrows(UserNotFoundException.class, () -> service.issue(unknown, PURPOSE));
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

    @Test
    void concurrentIssuesForTheSameUserAndPurpose_leaveExactlyOneActiveToken() throws Exception {
        UserId user = newUser();
        for (int round = 0; round < ROUNDS; round++) {
            List<RawOneTimeToken> issued = race(() -> issue(user), () -> issue(user));

            assertEquals(1, activeTokenCount(user), "round " + round);
            long consumable = issued.stream()
                    .filter(token -> service.findOwner(token.value(), PURPOSE).isPresent())
                    .count();
            assertEquals(1, consumable, "round " + round);
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
                        Optional<UserId> owner = service.findOwner(token.value(), PURPOSE);
                        if (owner.isEmpty()) {
                            return Optional.empty();
                        }
                        jdbcTemplate.queryForObject("SELECT id FROM users WHERE id = ? FOR NO KEY UPDATE",
                                UUID.class, owner.get().getId());
                        Optional<UserId> consumed = service.consume(token.value(), PURPOSE);
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
     * The documented flow compares the user from {@link OneTimeTokenService#findOwner} with the one from
     * {@link OneTimeTokenService#consume}, which are distinct instances: they must be equal by value.
     */
    @Test
    void findOwnerThenLockUserThenConsume_returnsAnEqualUser() {
        UserId user = newUser();
        RawOneTimeToken token = issue(user);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            Optional<UserId> owner = service.findOwner(token.value(), PURPOSE);
            assertTrue(owner.isPresent());
            jdbcTemplate.queryForObject("SELECT id FROM users WHERE id = ? FOR NO KEY UPDATE", UUID.class,
                    owner.get().getId());
            Optional<UserId> consumed = service.consume(token.value(), PURPOSE);

            assertTrue(consumed.isPresent());
            assertTrue(consumed.get().equals(owner.get()), "consume returned another user than findOwner");
            assertTrue(consumed.get().equals(user), "consume returned another user than the token's");
            assertEquals(owner.get().hashCode(), consumed.get().hashCode());
        });
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

        assertEquals(3, service.countIssuedSince(user, PURPOSE, t0.minus(microsecond)));
        assertEquals(3, service.countIssuedSince(user, PURPOSE, t0));
        assertEquals(2, service.countIssuedSince(user, PURPOSE, t0.plus(microsecond)));
        assertEquals(2, service.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(1))));
        assertEquals(1, service.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(1)).plus(microsecond)));
        assertEquals(1, service.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(2))));
        assertEquals(0, service.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(2)).plus(microsecond)));
        assertEquals(0, service.countIssuedSince(user, PURPOSE, t0.plus(Duration.ofHours(3))));
        assertEquals(1, service.countIssuedSince(otherUser, PURPOSE, t0));
    }

    @Test
    void countIssuedSince_refusesAWindowLongerThanTheRetention() {
        UserId user = newUser();
        Instant oldestCountable = clock.instant().minus(OneTimeTokenRetention.PERIOD);

        assertDoesNotThrow(() -> service.countIssuedSince(user, PURPOSE, oldestCountable));
        assertThrows(IllegalArgumentException.class,
                () -> service.countIssuedSince(user, PURPOSE, oldestCountable.minusNanos(1_000)));
    }

    @Test
    void invalidTokens_areRefused_exactlyLikeAnExpiredOne() {
        UserId user = newUser();
        RawOneTimeToken expired = issue(user);
        clock.advance(LIFETIME.plusSeconds(1));
        Optional<UUID> expiredOutcome = consume(expired.value());
        Optional<UserId> expiredOwner = service.findOwner(expired.value(), PURPOSE);

        List<String> invalid = new ArrayList<>(List.of("", "   ", "%%%", "short", "a".repeat(43), "a".repeat(500),
                randomRawToken()));
        invalid.add(null);
        for (String token : invalid) {
            assertEquals(expiredOutcome, consume(token));
            assertEquals(expiredOwner.map(UserId::getId), service.findOwner(token, PURPOSE).map(UserId::getId));
        }
        assertEquals(Optional.empty(), expiredOutcome);
    }

    @Test
    void findOwner_returnsTheUser_withoutConsumingTheToken() {
        UserId user = newUser();
        RawOneTimeToken token = issue(user);

        assertEquals(Optional.of(user.getId()), service.findOwner(token.value(), PURPOSE).map(UserId::getId));
        assertEquals(Optional.of(user.getId()), service.findOwner(token.value(), PURPOSE).map(UserId::getId));
        assertEquals(Optional.of(user.getId()), consume(token.value()));
        assertEquals(Optional.empty(), service.findOwner(token.value(), PURPOSE));
    }

    @Test
    void findOwner_ofARevokedToken_isEmpty() {
        UserId user = newUser();
        RawOneTimeToken revoked = issue(user);
        issue(user);

        assertEquals(Optional.empty(), service.findOwner(revoked.value(), PURPOSE));
    }

    @Test
    void deletingAUser_deletesTheirTokens() {
        UserId user = newUser();
        issue(user);

        deleteUserRepository.delete(user);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM one_time_token WHERE user_id = ?", Integer.class, user.getId()));
    }

    private UserId newUser() {
        UUID id = userRepository.save(UserMother.randomUserEntity()).getId();
        users.add(id);
        return UserId.of(id);
    }

    private RawOneTimeToken issue(UserId user) {
        RawOneTimeToken token = service.issue(user, PURPOSE);
        remember(token.value());
        return token;
    }

    private Optional<UUID> consume(String rawToken) {
        return service.consume(rawToken, PURPOSE).map(UserId::getId);
    }

    private String randomRawToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        remember(raw);
        return raw;
    }

    private void remember(String raw) {
        secrets.add(raw);
        secrets.add(sha256(raw));
    }

    private int activeTokenCount(UserId user) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token "
                + "WHERE user_id = ? AND used_at IS NULL AND revoked_at IS NULL", Integer.class, user.getId());
    }

    /**
     * Runs both tasks on their own threads, released together once both are ready. Fails if either throws.
     */
    private static <T> List<T> race(Callable<? extends T> first, Callable<? extends T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<? extends T>> futures = new ArrayList<>();
            for (Callable<? extends T> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(go.await(10, TimeUnit.SECONDS));
                    return task.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            List<T> outcomes = new ArrayList<>();
            for (Future<? extends T> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
