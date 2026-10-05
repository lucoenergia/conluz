package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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
 * An attempt must reserve a slot before its password is checked, and the reservation is held until the attempt is
 * settled: confirmed as a failure, or released. A key whose failures plus reservations reach the limit accepts no
 * further reservation, so concurrent attempts cannot check more passwords than the limit allows, however many of
 * them arrive before the first one fails.
 * <p>
 * Keys are supplied by clients, so memory is bounded: at most {@link #MAX_ENTRIES} windows are held, the window that
 * started first is evicted beyond that, and keys are cut to {@link #MAX_KEY_LENGTH} characters. Windows are kept in
 * the order they started, so the expired ones are always at the head and are purged on every write. Reservations
 * are held only by requests being processed, so their number is bounded by the server's concurrency, and a key's
 * entry is dropped when its last reservation is settled.
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
    private final Map<String, Integer> reservations = new HashMap<>();

    FailedAttemptCounter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    /**
     * Reserves a slot for one attempt, unless the key's failures plus the attempts already reserved reach the limit.
     * A successful reservation must be settled with {@link #confirmFailure} or {@link #release}.
     *
     * @return empty if the slot was reserved; otherwise the seconds, rounded up, to wait before retrying: until the
     * key's window ends, or a whole window if no failure has started one yet, since the attempts in progress would
     */
    synchronized OptionalLong tryReserve(String key, Instant now) {
        String cutKey = cut(key);
        Window current = currentWindow(cutKey, now);
        int failures = current == null ? 0 : current.count;
        if (failures + reservations.getOrDefault(cutKey, 0) >= limit) {
            Duration wait = current == null ? window : Duration.between(now, current.end);
            return OptionalLong.of(secondsRoundedUp(wait));
        }
        reservations.merge(cutKey, 1, Integer::sum);
        return OptionalLong.empty();
    }

    /**
     * Settles a reservation without counting a failure.
     */
    synchronized void release(String key) {
        reservations.computeIfPresent(cut(key), (k, reserved) -> reserved > 1 ? reserved - 1 : null);
    }

    /**
     * Settles a reservation as one failure.
     *
     * @return {@code true} if this failure made the key reach the limit
     */
    synchronized boolean confirmFailure(String key, Instant now) {
        release(key);
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

    /**
     * @return the seconds, rounded up, until the key's window ends if its failures alone reach the limit; empty
     * otherwise
     */
    synchronized OptionalLong retryAfterSeconds(String key, Instant now) {
        Window current = currentWindow(cut(key), now);
        if (current == null || current.count < limit) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(secondsRoundedUp(Duration.between(now, current.end)));
    }

    /**
     * Forgets the key's failures. Reservations in progress are kept: each is settled by its own attempt.
     */
    synchronized void reset(String key) {
        windows.remove(cut(key));
    }

    synchronized int size() {
        return windows.size();
    }

    synchronized int reservationCount() {
        return reservations.size();
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
