package org.lucoenergia.conluz.domain.admin.user.token;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against the real database and commits, since concurrent callers must see each other's rows. Every test
 * creates its own users and deletes them afterwards, which deletes their tokens too. Every test also checks that no
 * log line contains a token it produced or that token's hash.
 */
abstract class BaseOneTimeTokenServiceTest extends BaseIntegrationTest {

    protected static final OneTimeTokenPurpose PURPOSE = OneTimeTokenPurpose.PASSWORD_RESET;
    protected static final Duration LIFETIME = Duration.ofDays(1);
    protected static final int ROUNDS = 10;

    @Autowired
    protected CreateOneTimeTokenService createService;
    @Autowired
    protected ConsumeOneTimeTokenService consumeService;
    @Autowired
    protected GetOneTimeTokenService getService;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected DeleteUserRepository deleteUserRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected PlatformTransactionManager transactionManager;

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

    protected UserId newUser() {
        UUID id = userRepository.save(UserMother.randomUserEntity()).getId();
        users.add(id);
        return UserId.of(id);
    }

    protected RawOneTimeToken issue(UserId user) {
        RawOneTimeToken token = createService.issue(user, PURPOSE);
        remember(token.value());
        return token;
    }

    protected Optional<UUID> consume(String rawToken) {
        return consumeService.consume(rawToken, PURPOSE).map(UserId::getId);
    }

    protected String randomRawToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        remember(raw);
        return raw;
    }

    protected void remember(String raw) {
        secrets.add(raw);
        secrets.add(sha256(raw));
    }

    protected int activeTokenCount(UserId user) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token "
                + "WHERE user_id = ? AND used_at IS NULL AND revoked_at IS NULL", Integer.class, user.getId());
    }

    /**
     * Runs both tasks on their own threads, released together once both are ready. Fails if either throws.
     */
    protected static <T> List<T> race(Callable<? extends T> first, Callable<? extends T> second) throws Exception {
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

    protected static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
