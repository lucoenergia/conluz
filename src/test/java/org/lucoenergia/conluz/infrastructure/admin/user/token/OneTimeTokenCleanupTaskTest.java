package org.lucoenergia.conluz.infrastructure.admin.user.token;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scheduling is off in tests, so the job is called directly. Every token here belongs to a user created by the
 * test, and those users are deleted afterwards.
 */
class OneTimeTokenCleanupTaskTest extends BaseIntegrationTest {

    private static final OneTimeTokenPurpose PURPOSE = OneTimeTokenPurpose.PASSWORD_RESET;

    @Autowired
    private OneTimeTokenCleanupTask task;
    @Autowired
    private OneTimeTokenService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;

    private final List<UUID> users = new ArrayList<>();

    @AfterEach
    void deleteTheUsers() {
        users.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    }

    /**
     * With the cleanup at T0+9d+1h the cutoff is T0+2d+1h. Each deleted token is past it by one criterion only.
     */
    @Test
    void deletesTokensFinishedBeforeTheRetention_andKeepsTheOthers() {
        List<String> secrets = new ArrayList<>();
        UserId expiredUser = newUser();
        UserId expiredJustBeforeUser = newUser();
        UserId expiringAtCutoffUser = newUser();
        UserId usedUser = newUser();
        UserId revokedUser = newUser();
        UserId activeUser = newUser();

        // T0
        RawOneTimeToken expired = issue(expiredUser, secrets);
        // T0+1d+1h-1s: expires one second before the cutoff
        clock.advance(Duration.ofDays(1).plusHours(1).minusSeconds(1));
        RawOneTimeToken expiredJustBefore = issue(expiredJustBeforeUser, secrets);
        // T0+1d+1h: expires at the cutoff, which is not before it
        clock.advance(Duration.ofSeconds(1));
        RawOneTimeToken expiringAtCutoff = issue(expiringAtCutoffUser, secrets);
        // T0+2d: used, and revoked, an hour before the cutoff, both expiring after it
        clock.advance(Duration.ofHours(23));
        RawOneTimeToken used = issue(usedUser, secrets);
        assertTrue(service.consume(used.value(), PURPOSE).isPresent());
        RawOneTimeToken revoked = issue(revokedUser, secrets);
        RawOneTimeToken replacement = issue(revokedUser, secrets);
        // T0+9d+1h
        clock.advance(Duration.ofDays(7).plusHours(1));
        RawOneTimeToken active = issue(activeUser, secrets);

        try (LogCapture logs = LogCapture.ofRoot()) {
            task.cleanup();

            for (ILoggingEvent event : logs.all()) {
                for (String secret : secrets) {
                    assertFalse(event.getFormattedMessage().contains(secret),
                            "A log line contains a one-time token or its hash");
                }
            }
        }

        assertFalse(stored(expired), "expired before the retention");
        assertFalse(stored(expiredJustBefore), "expired one second before the cutoff");
        assertFalse(stored(used), "used before the retention");
        assertFalse(stored(revoked), "revoked before the retention");
        assertTrue(stored(expiringAtCutoff), "expires at the cutoff");
        assertTrue(stored(replacement), "expired within the retention");
        assertTrue(stored(active), "active");
    }

    private UserId newUser() {
        UUID id = userRepository.save(UserMother.randomUserEntity()).getId();
        users.add(id);
        return UserId.of(id);
    }

    private RawOneTimeToken issue(UserId user, List<String> secrets) {
        RawOneTimeToken token = service.issue(user, PURPOSE);
        secrets.add(token.value());
        secrets.add(OneTimeTokenServiceImpl.hash(token.value()));
        return token;
    }

    private boolean stored(RawOneTimeToken token) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE token_hash = ?", Integer.class,
                OneTimeTokenServiceImpl.hash(token.value())) == 1;
    }
}
