package org.lucoenergia.conluz.infrastructure.shared.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.fail;

/**
 * Orders two transactions deterministically, without sleeping: the first one holds its transaction open until the
 * second is blocked on a lock, or has finished because nothing blocked it.
 */
public final class LockWaitTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private LockWaitTestSupport() {
    }

    /**
     * Runs {@code first} in a transaction and, while it is still open, starts {@code second} in another one. Commits
     * {@code first} once {@code second} is blocked on a lock or has finished, and returns how {@code second} ended:
     * {@code null} if it succeeded, or what it threw.
     */
    public static Throwable runWhileHeld(PlatformTransactionManager transactionManager, JdbcTemplate jdbcTemplate,
                                         Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> competing = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                first.run();
                competing.set(pool.submit(second));
                awaitWaitingOnALockOrDone(jdbcTemplate, competing.get());
            });
            try {
                competing.get().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                return null;
            } catch (ExecutionException e) {
                return e.getCause();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Reads {@code pg_stat_activity} rather than sleeping. Any lock counts, not only the row lock the services take:
     * without it, the competitor would block on a foreign-key check instead, and the outcome asserted is what tells
     * the two apart.
     */
    private static void awaitWaitingOnALockOrDone(JdbcTemplate jdbcTemplate, Future<?> competing) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (!competing.isDone() && !anotherSessionIsWaitingOnALock(jdbcTemplate)) {
            if (Instant.now().isAfter(deadline)) {
                fail("The competing transaction neither finished nor waited on a lock within " + TIMEOUT);
            }
            Thread.onSpinWait();
        }
    }

    /**
     * Runs inside the first transaction, where PostgreSQL would otherwise answer every poll from the activity
     * snapshot it took on the first one; clearing it makes each poll see the competitor's current state.
     */
    private static boolean anotherSessionIsWaitingOnALock(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
        Integer waiting = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_stat_activity "
                + "WHERE wait_event_type = 'Lock' AND datname = current_database() AND pid <> pg_backend_pid()",
                Integer.class);
        return waiting != null && waiting > 0;
    }
}
