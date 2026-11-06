package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationThresholdTest {

    @Test
    @DisplayName("ENM-001 exactly 90 % of the possible hours reaches the threshold")
    void exactlyNinetyPercentReachesTheThreshold() {
        assertTrue(PublicationThreshold.isReached(648, 720));
    }

    @Test
    @DisplayName("ENM-001 one hour below 90 % of the possible hours does not reach the threshold")
    void oneHourBelowNinetyPercentDoesNotReachTheThreshold() {
        assertFalse(PublicationThreshold.isReached(647, 720));
    }

    @Test
    void everyPossibleHourReachesTheThreshold() {
        assertTrue(PublicationThreshold.isReached(745, 745));
    }

    @Test
    void noPublishedHourDoesNotReachTheThreshold() {
        assertFalse(PublicationThreshold.isReached(0, 24));
    }

    /**
     * 90 % of 743 hours is 668.7: the threshold is not rounded down to 668.
     */
    @Test
    void aThresholdFallingBetweenTwoHoursIsNotRoundedDown() {
        assertFalse(PublicationThreshold.isReached(668, 743));
        assertTrue(PublicationThreshold.isReached(669, 743));
    }
}
