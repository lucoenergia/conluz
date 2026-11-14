package org.lucoenergia.conluz.infrastructure.shared.email;

import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.AopTestUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Waits until every email an {@link AfterCommitEmailSender} has dispatched is delivered, so that a test reads only
 * the emails it caused (#571).
 *
 * <p>One barrier task per thread of the sender's executor is queued behind the deliveries. The queue is first in,
 * first out, so once every barrier task is running at the same time, every delivery queued before them has
 * finished. The SMTP transport returns only once the server has stored the email, so by then every email
 * dispatched has arrived. A send whose transaction rolled back, or whose call a test made throw, dispatches
 * nothing, and is not waited for.</p>
 *
 * <p>No production API exposes the sender's executor, so this reads its private {@value #EXECUTOR_FIELD} field by
 * reflection. If the field is missing, is not a {@link ThreadPoolTaskExecutor}, or holds a pool that can grow past
 * its core size, it fails loudly instead of skipping the wait or draining partially: either would let an email reach
 * the next test.</p>
 */
public final class EmailDispatchTestSupport {

    static final String EXECUTOR_FIELD = "executor";

    private EmailDispatchTestSupport() {
    }

    /**
     * Waits up to {@code timeoutMillis} for every email the sender has dispatched to be delivered, and returns how
     * many deliveries are still pending: 0 once they are all delivered.
     */
    public static int drain(EmailSender sender, long timeoutMillis) {
        ThreadPoolExecutor pool = executorOf(sender).getThreadPoolExecutor();
        int threads = pool.getCorePoolSize();
        if (pool.getMaximumPoolSize() != threads) {
            // A full queue would start extra threads, which could run every barrier task while a delivery still runs
            throw new IllegalStateException("The executor's core pool size (" + threads + ") differs from its max pool"
                    + " size (" + pool.getMaximumPoolSize() + "): the email test harness cannot wait for its deliveries");
        }
        CountDownLatch running = new CountDownLatch(threads);
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                running.countDown();
                awaitUninterruptibly(release);
            });
        }
        try {
            if (await(running, timeoutMillis)) {
                return 0;
            }
            // Every barrier task is still either queued or running, so it is not one of the deliveries pending
            return pool.getQueue().size() + pool.getActiveCount() - threads;
        } finally {
            release.countDown();
        }
    }

    /**
     * Drains the sender, and fails, naming {@code testName} and how many deliveries are still pending, if it is not
     * drained within {@code timeoutMillis}: a test that leaves an email undelivered fails itself, instead of letting it
     * reach the next test's mailbox (#571). Counts only, never an address.
     */
    public static void assertDrained(EmailSender sender, long timeoutMillis, String testName) {
        int pending = drain(sender, timeoutMillis);
        if (pending > 0) {
            throw new AssertionError(String.format("%s: %d email deliveries still pending after %d ms", testName,
                    pending, timeoutMillis));
        }
    }

    static ThreadPoolTaskExecutor executorOf(EmailSender sender) {
        Object target = AopTestUtils.getUltimateTargetObject(sender);
        Field field = ReflectionUtils.findField(target.getClass(), EXECUTOR_FIELD);
        if (field == null) {
            throw new IllegalStateException(target.getClass().getName() + " has no field '" + EXECUTOR_FIELD
                    + "': the email test harness cannot wait for its deliveries");
        }
        ReflectionUtils.makeAccessible(field);
        Object executor = ReflectionUtils.getField(field, target);
        if (!(executor instanceof ThreadPoolTaskExecutor taskExecutor)) {
            throw new IllegalStateException("The field '" + EXECUTOR_FIELD + "' of " + target.getClass().getName()
                    + " is not a ThreadPoolTaskExecutor: the email test harness cannot wait for its deliveries");
        }
        return taskExecutor;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException e) {
                // A barrier task is released only by its drain
            }
        }
    }

    private static boolean await(CountDownLatch latch, long timeoutMillis) {
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return latch.getCount() == 0;
        }
    }
}
