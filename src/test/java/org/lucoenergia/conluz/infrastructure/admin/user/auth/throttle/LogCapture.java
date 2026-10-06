package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Captures the events logged at WARN or above while it is open, on one logger and its descendants.
 */
public class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
        appender.start();
        logger.addAppender(appender);
    }

    /**
     * Captures everything the application and its libraries log.
     */
    public static LogCapture ofRoot() {
        return new LogCapture((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME));
    }

    public static LogCapture of(Class<?> loggerClass) {
        return new LogCapture((Logger) LoggerFactory.getLogger(loggerClass));
    }

    /**
     * Every event logged, by any thread, at any level the logger configuration lets through. A copy, taken under
     * the appender's lock, so that it can be read while other threads are still logging.
     */
    public List<ILoggingEvent> all() {
        synchronized (appender) {
            return List.copyOf(appender.list);
        }
    }

    /**
     * Every event logged by the calling thread, at any level the logger configuration lets through.
     */
    public List<ILoggingEvent> allOfThisThread() {
        String thread = Thread.currentThread().getName();
        return all().stream()
                .filter(event -> event.getThreadName().equals(thread))
                .toList();
    }

    public List<ILoggingEvent> warningsAndAbove() {
        return all().stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .toList();
    }

    /**
     * The WARN-or-above events logged by the calling thread: a MockMvc request runs on it, a scheduled job or
     * another test's server does not.
     */
    public List<ILoggingEvent> warningsAndAboveOfThisThread() {
        String thread = Thread.currentThread().getName();
        return warningsAndAbove().stream()
                .filter(event -> event.getThreadName().equals(thread))
                .toList();
    }

    public void clear() {
        synchronized (appender) {
            appender.list.clear();
        }
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
