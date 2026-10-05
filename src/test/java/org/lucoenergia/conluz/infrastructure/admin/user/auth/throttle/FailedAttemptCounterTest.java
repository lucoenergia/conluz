package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailedAttemptCounterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private final FailedAttemptCounter counter = new FailedAttemptCounter(3, WINDOW);

    @Test
    void aKeyIsBlockedOnlyOnceItReachesTheLimit() {
        assertFalse(counter.recordFailure("key", START));
        assertFalse(counter.recordFailure("key", START));
        assertEquals(OptionalLong.empty(), counter.retryAfterSeconds("key", START));

        assertTrue(counter.recordFailure("key", START));

        assertEquals(OptionalLong.of(900), counter.retryAfterSeconds("key", START));
    }

    @Test
    void theWindowStartsAtTheFirstFailure_notTheLast() {
        counter.recordFailure("key", START);
        counter.recordFailure("key", START.plus(Duration.ofMinutes(10)));
        counter.recordFailure("key", START.plus(Duration.ofMinutes(14)));

        assertEquals(OptionalLong.of(60), counter.retryAfterSeconds("key", START.plus(Duration.ofMinutes(14))));
    }

    @Test
    void retryAfterIsRoundedUpToTheNextWholeSecond() {
        fillUp("key", START);

        assertEquals(OptionalLong.of(899), counter.retryAfterSeconds("key", START.plusMillis(1_500)));
        assertEquals(OptionalLong.of(1), counter.retryAfterSeconds("key", START.plus(WINDOW).minusNanos(1)));
    }

    @Test
    void theKeyIsReleasedWhenTheWindowEnds_andTheNextFailureStartsANewWindow() {
        fillUp("key", START);
        Instant end = START.plus(WINDOW);

        assertEquals(OptionalLong.empty(), counter.retryAfterSeconds("key", end));

        assertFalse(counter.recordFailure("key", end));
        assertFalse(counter.recordFailure("key", end));
        assertTrue(counter.recordFailure("key", end));
        assertEquals(OptionalLong.of(900), counter.retryAfterSeconds("key", end));
    }

    @Test
    void failuresInAnEndedWindowDoNotAddToTheNextOne() {
        counter.recordFailure("key", START);
        counter.recordFailure("key", START);

        assertFalse(counter.recordFailure("key", START.plus(WINDOW)));
        assertEquals(OptionalLong.empty(), counter.retryAfterSeconds("key", START.plus(WINDOW)));
    }

    @Test
    void resetForgetsTheKeyOnly() {
        fillUp("key", START);
        fillUp("other", START);

        counter.reset("key");

        assertEquals(OptionalLong.empty(), counter.retryAfterSeconds("key", START));
        assertEquals(OptionalLong.of(900), counter.retryAfterSeconds("other", START));
    }

    @Test
    void keysAreCountedSeparately() {
        counter.recordFailure("a", START);
        counter.recordFailure("b", START);
        counter.recordFailure("c", START);

        assertEquals(OptionalLong.empty(), counter.retryAfterSeconds("a", START));
    }

    @Test
    void moreKeysThanTheBound_keepTheCounterAtTheBound_byEvictingTheOldestWindows() {
        for (int i = 0; i < FailedAttemptCounter.MAX_ENTRIES + 1_000; i++) {
            counter.recordFailure("key-" + i, START.plusMillis(i));
        }

        assertEquals(FailedAttemptCounter.MAX_ENTRIES, counter.size());

        // The newest key is still counted, the oldest one was evicted
        Instant now = START.plusMillis(FailedAttemptCounter.MAX_ENTRIES + 1_000);
        int newest = FailedAttemptCounter.MAX_ENTRIES + 999;
        counter.recordFailure("key-" + newest, now);
        assertTrue(counter.recordFailure("key-" + newest, now));
        counter.recordFailure("key-0", now);
        assertFalse(counter.recordFailure("key-0", now));
    }

    @Test
    void endedWindowsAreDroppedOnTheNextWrite() {
        counter.recordFailure("a", START);
        counter.recordFailure("b", START.plusSeconds(1));
        counter.recordFailure("c", START.plus(WINDOW).minusSeconds(1));

        counter.recordFailure("d", START.plus(WINDOW).plusSeconds(1));

        assertEquals(2, counter.size());
    }

    @Test
    void keysAreCutToTheMaximumLength_soAnOversizedKeyCannotTakeMoreMemory() {
        String longKey = "X".repeat(FailedAttemptCounter.MAX_KEY_LENGTH);

        counter.recordFailure(longKey + "A".repeat(10_000), START);
        counter.recordFailure(longKey + "B".repeat(10_000), START);

        assertEquals(1, counter.size());
    }

    private void fillUp(String key, Instant now) {
        for (int i = 0; i < 3; i++) {
            counter.recordFailure(key, now);
        }
    }
}
