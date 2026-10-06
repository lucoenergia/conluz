package org.lucoenergia.conluz.infrastructure.shared.email;

import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Sends emails on a small pool of its own threads, once the transaction that asked for them has committed.
 *
 * <p>The pool is bounded: when its queue is full, an email is dropped with a warning instead of piling up. It is
 * not a bean on purpose: any {@code Executor} bean makes Spring Boot leave out the application task executor it
 * otherwise provides.</p>
 *
 * <p>Nothing about an email but its category is ever logged: the body can carry one-time tokens, and the
 * recipient is personal data. A failure is logged by its exception type alone, since mail exception messages
 * quote the addresses involved.</p>
 */
@Component
public class AfterCommitEmailSender implements EmailSender, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(AfterCommitEmailSender.class);

    static final String THREAD_NAME_PREFIX = "email-";
    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 100;
    /**
     * Long enough for an email in flight to run into its SMTP timeouts.
     */
    private static final int SHUTDOWN_SECONDS = 20;

    private final EmailTransport transport;
    private final ThreadPoolTaskExecutor executor;

    @Autowired
    public AfterCommitEmailSender(EmailTransport transport) {
        this(transport, THREADS, QUEUE_CAPACITY);
    }

    AfterCommitEmailSender(EmailTransport transport, int threads, int queueCapacity) {
        this.transport = transport;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_SECONDS);
        executor.initialize();
    }

    @Override
    public void send(Email email) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(email);
                }
            });
        } else {
            dispatch(email);
        }
    }

    /**
     * Never throws: when called after a commit, an exception would reach the caller of a transaction that has
     * already succeeded.
     */
    private void dispatch(Email email) {
        try {
            executor.execute(() -> deliver(email));
        } catch (TaskRejectedException e) {
            LOGGER.warn("Email {} dropped: the sending queue is full", email.category());
        }
    }

    private void deliver(Email email) {
        try {
            transport.send(email);
        } catch (Exception e) {
            LOGGER.warn("Email {} not sent: {}", email.category(), e.getClass().getSimpleName());
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
