package org.lucoenergia.conluz.infrastructure.shared.email;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.lucoenergia.conluz.infrastructure.shared.email.EmailTestData.email;

class EmailDispatchTestSupportTest {

    private static final int THREADS = 2;

    private final List<Email> delivered = new CopyOnWriteArrayList<>();
    private AfterCommitEmailSender sender;

    @AfterEach
    void stopTheSender() {
        if (sender != null) {
            sender.destroy();
        }
    }

    @Test
    void waitsForEveryDispatchedEmail_includingTheQueuedOnes() {
        sender = new AfterCommitEmailSender(email -> {
            Thread.sleep(300);
            delivered.add(email);
        }, THREADS, 100);
        for (int i = 0; i < THREADS + 1; i++) {
            sender.send(email("DRAIN"));
        }

        assertThat(EmailDispatchTestSupport.drain(sender, 5_000)).isZero();

        assertThat(delivered).hasSize(THREADS + 1);
    }

    @Test
    void anIdleSender_isDrained() {
        sender = new AfterCommitEmailSender(delivered::add, THREADS, 100);

        assertThat(EmailDispatchTestSupport.drain(sender, 5_000)).isZero();
    }

    @Test
    void onTimeout_theDeliveriesStillPendingAreCounted_andNoBarrierTaskStaysBlocked() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        sender = new AfterCommitEmailSender(email -> {
            assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();
            delivered.add(email);
        }, THREADS, 100);
        // Two deliveries running and one queued
        for (int i = 0; i < THREADS + 1; i++) {
            sender.send(email("DRAIN"));
        }

        assertThat(EmailDispatchTestSupport.drain(sender, 200)).isEqualTo(THREADS + 1);

        blocked.countDown();
        // Would time out if the barrier tasks of the first drain still held the threads
        assertThat(EmailDispatchTestSupport.drain(sender, 5_000)).isZero();
        assertThat(delivered).hasSize(THREADS + 1);
    }

    @Test
    void anUndrainedSender_failsNamingTheTestAndThePendingCount() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        sender = new AfterCommitEmailSender(email -> {
            assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();
            delivered.add(email);
        }, THREADS, 100);
        sender.send(email("DRAIN"));

        try {
            assertThatThrownBy(() -> EmailDispatchTestSupport.assertDrained(sender, 200, "SomeTest > some test"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("SomeTest > some test: 1 email deliveries still pending after 200 ms");
        } finally {
            blocked.countDown();
        }
        EmailDispatchTestSupport.assertDrained(sender, 5_000, "SomeTest > some test");
        assertThat(delivered).hasSize(1);
    }

    @Test
    void anExecutorThatCanGrowPastItsCoreSize_failsLoudly() {
        SenderWithAGrowingExecutor growing = new SenderWithAGrowingExecutor();
        try {
            assertThatThrownBy(() -> EmailDispatchTestSupport.drain(growing, 1_000))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("core pool size (1) differs from its max pool size (2)");
        } finally {
            growing.executor.shutdown();
        }
    }

    @Test
    void aSenderWithoutTheExecutorField_failsLoudly() {
        EmailSender withoutExecutor = email -> {
        };

        assertThatThrownBy(() -> EmailDispatchTestSupport.drain(withoutExecutor, 1_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no field 'executor'");
    }

    @Test
    void anExecutorFieldOfAnotherType_failsLoudly() {
        assertThatThrownBy(() -> EmailDispatchTestSupport.drain(new SenderWithAnotherExecutor(), 1_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not a ThreadPoolTaskExecutor");
    }

    private static final class SenderWithAGrowingExecutor implements EmailSender {

        private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        private SenderWithAGrowingExecutor() {
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(1);
            executor.initialize();
        }

        @Override
        public void send(Email email) {
        }
    }

    private static final class SenderWithAnotherExecutor implements EmailSender {

        @SuppressWarnings("unused")
        private final Object executor = new Object();

        @Override
        public void send(Email email) {
        }
    }
}
