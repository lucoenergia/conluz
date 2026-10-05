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
        assertFalse(fail("key", START));
        assertFalse(fail("key", START));
        assertEquals(OptionalLong.empty(), counter.retryAfterSeconds("key", START));

        assertTrue(fail("key", START));

        assertEquals(OptionalLong.of(900), counter.retryAfterSeconds("key", START));
    }

    @Test
    void theWindowStartsAtTheFirstFailure_notTheLast() {
        fail("key", START);
        fail("key", START.plus(Duration.ofMinutes(10)));
        fail("key", START.plus(Duration.ofMinutes(14)));

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

        assertFalse(fail("key", end));
        assertFalse(fail("key", end));
        assertTrue(fail("key", end));
        assertEquals(OptionalLong.of(900), counter.retryAfterSeconds("key", end));
    }

    @Test
    void failuresInAnEndedWindowDoNotAddToTheNextOne() {
        fail("key", START);
        fail("key", START);

        assertFalse(fail("key", START.plus(WINDOW)));
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
        fail("a", START);
        fail("b", START);
        fail("c", START);

        assertEquals(OptionalLong.empty(), counter.retryAfterSeconds("a", START));
    }

    @Test
    void moreKeysThanTheBound_keepTheCounterAtTheBound_byEvictingTheOldestWindows() {
        for (int i = 0; i < FailedAttemptCounter.MAX_ENTRIES + 1_000; i++) {
            fail("key-" + i, START.plusMillis(i));
        }

        assertEquals(FailedAttemptCounter.MAX_ENTRIES, counter.size());

        // The newest key is still counted, the oldest one was evicted
        Instant now = START.plusMillis(FailedAttemptCounter.MAX_ENTRIES + 1_000);
        int newest = FailedAttemptCounter.MAX_ENTRIES + 999;
        fail("key-" + newest, now);
        assertTrue(fail("key-" + newest, now));
        fail("key-0", now);
        assertFalse(fail("key-0", now));
    }

    @Test
    void endedWindowsAreDroppedOnTheNextWrite() {
        fail("a", START);
        fail("b", START.plusSeconds(1));
        fail("c", START.plus(WINDOW).minusSeconds(1));

        fail("d", START.plus(WINDOW).plusSeconds(1));

        assertEquals(2, counter.size());
    }

    @Test
    void keysAreCutToTheMaximumLength_soAnOversizedKeyCannotTakeMoreMemory() {
        String longKey = "X".repeat(FailedAttemptCounter.MAX_KEY_LENGTH);

        fail(longKey + "A".repeat(10_000), START);
        fail(longKey + "B".repeat(10_000), START);

        assertEquals(1, counter.size());
    }

    @Test
    void attemptsInProgress_countTowardsTheLimit_soAKeyCannotReserveMoreSlotsThanItHasLeft() {
        fail("key", START);
        reserve("key", START);

        assertEquals(OptionalLong.empty(), counter.tryReserve("key", START));
        assertEquals(OptionalLong.of(900), counter.tryReserve("key", START));
        assertEquals(OptionalLong.of(899), counter.tryReserve("key", START.plusMillis(1_500)));
    }

    @Test
    void attemptsInProgressBeforeAnyFailure_areRefusedForAWholeWindow_sinceTheirFailuresWouldStartOne() {
        reserve("key", START);
        reserve("key", START);
        reserve("key", START);

        assertEquals(OptionalLong.of(900), counter.tryReserve("key", START));
    }

    @Test
    void aReleasedSlot_canBeReservedAgain_andCountsNoFailure() {
        reserve("key", START);
        reserve("key", START);
        reserve("key", START);

        counter.release("key");

        assertEquals(OptionalLong.empty(), counter.tryReserve("key", START));
        assertEquals(OptionalLong.empty(), counter.retryAfterSeconds("key", START));
    }

    @Test
    void settlingTheLastReservation_dropsTheKeysReservationEntry() {
        reserve("a", START);
        reserve("b", START);
        reserve("b", START);

        counter.release("a");
        counter.release("b");
        counter.confirmFailure("b", START);

        assertEquals(0, counter.reservationCount());
    }

    @Test
    void resetForgetsTheFailures_butNotTheAttemptsInProgress() {
        fail("key", START);
        reserve("key", START);
        reserve("key", START);

        counter.reset("key");

        assertEquals(OptionalLong.empty(), counter.tryReserve("key", START));
        assertEquals(OptionalLong.of(900), counter.tryReserve("key", START));
    }

    private void reserve(String key, Instant now) {
        assertEquals(OptionalLong.empty(), counter.tryReserve(key, now));
    }

    /**
     * One attempt that is admitted and then fails.
     *
     * @return {@code true} if this failure made the key reach the limit
     */
    private boolean fail(String key, Instant now) {
        counter.tryReserve(key, now);
        return counter.confirmFailure(key, now);
    }

    private void fillUp(String key, Instant now) {
        for (int i = 0; i < 3; i++) {
            fail(key, now);
        }
    }
}
