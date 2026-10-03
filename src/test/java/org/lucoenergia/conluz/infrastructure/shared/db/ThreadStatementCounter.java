package org.lucoenergia.conluz.infrastructure.shared.db;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Counts the SQL statements Hibernate prepares on the current thread only.
 *
 * <p>{@code SessionFactory#getStatistics()} is shared by the whole application, so a statement
 * issued on another thread while a test is measuring -- the scheduler runs
 * {@code ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob} every 30 seconds, and it reads
 * the Shelly configuration -- is counted as if the measured code had issued it. This inspector
 * counts on a per-thread basis, and only while a measurement is open on that thread, so the count
 * covers exactly the work the test runs. MockMvc executes the request on the calling thread, so a
 * whole endpoint call is measured.</p>
 *
 * <p>Installed suite-wide through {@code hibernate.session_factory.statement_inspector} in
 * {@code application-test.properties}; outside a measurement it only returns the SQL unchanged.</p>
 */
public class ThreadStatementCounter implements StatementInspector {

    private static final ThreadLocal<long[]> COUNT = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
        long[] count = COUNT.get();
        if (count != null) {
            count[0]++;
        }
        return sql;
    }

    /**
     * Runs {@code work} and returns how many statements Hibernate prepared on this thread meanwhile.
     */
    public static <E extends Exception> long count(ThrowingRunnable<E> work) throws E {
        if (COUNT.get() != null) {
            throw new IllegalStateException("A statement count is already open on this thread");
        }
        long[] count = new long[1];
        COUNT.set(count);
        try {
            work.run();
        } finally {
            COUNT.remove();
        }
        return count[0];
    }

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }
}
