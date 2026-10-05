package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Counts failed attempts per key in a fixed window that starts at the key's first counted failure.
 * <p>
 * A key is blocked once it has {@code limit} failures and until its window ends. The first failure at or after
 * the end of a window starts a new one.
 * <p>
 * Keys are supplied by clients, so memory is bounded: at most {@link #MAX_ENTRIES} keys are held, the window that
 * started first is evicted beyond that, and keys are cut to {@link #MAX_KEY_LENGTH} characters. Windows are kept in
 * the order they started, so the expired ones are always at the head and are purged on every write.
 */
class FailedAttemptCounter {

    static final int MAX_ENTRIES = 10_000;
    static final int MAX_KEY_LENGTH = 64;

    private final int limit;
    private final Duration window;
    private final Map<String, Window> windows = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    FailedAttemptCounter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    /**
     * @return the seconds, rounded up, until the key's window ends if the key is blocked; empty otherwise
     */
    synchronized OptionalLong retryAfterSeconds(String key, Instant now) {
        Window current = currentWindow(cut(key), now);
        if (current == null || current.count < limit) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(secondsRoundedUp(Duration.between(now, current.end)));
    }

    /**
     * Counts one failure for the key.
     *
     * @return {@code true} if this failure made the key reach the limit
     */
    synchronized boolean recordFailure(String key, Instant now) {
        purgeExpired(now);
        String cutKey = cut(key);
        Window current = windows.get(cutKey);
        if (current == null) {
            current = new Window(now.plus(window));
            windows.put(cutKey, current);
        }
        current.count++;
        return current.count == limit;
    }

    synchronized void reset(String key) {
        windows.remove(cut(key));
    }

    synchronized int size() {
        return windows.size();
    }

    private Window currentWindow(String key, Instant now) {
        Window current = windows.get(key);
        if (current != null && !now.isBefore(current.end)) {
            windows.remove(key);
            return null;
        }
        return current;
    }

    private void purgeExpired(Instant now) {
        Iterator<Window> iterator = windows.values().iterator();
        while (iterator.hasNext() && !now.isBefore(iterator.next().end)) {
            iterator.remove();
        }
    }

    private static String cut(String key) {
        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key;
    }

    private static long secondsRoundedUp(Duration duration) {
        return duration.getNano() > 0 ? duration.getSeconds() + 1 : duration.getSeconds();
    }

    private static final class Window {

        private final Instant end;
        private int count;

        private Window(Instant end) {
            this.end = end;
        }
    }
}
