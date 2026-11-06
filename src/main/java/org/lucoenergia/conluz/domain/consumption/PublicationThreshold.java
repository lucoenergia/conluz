package org.lucoenergia.conluz.domain.consumption;

/**
 * Whether a supply's published hours reach the share of its possible hours that makes a period
 * published for it: at least {@link ReferenceMonthResolver#PUBLISHED_HOURS_MIN_PERCENT} of them.
 *
 * <p>The period is whatever the caller counts the hours over: a calendar month when the reference
 * month is resolved, any requested period when a community counts the supplies with data in it.
 * Counting the possible hours on the instant line, as {@link EnergyMetricsPeriod#getExpectedHours()}
 * does, is what makes a day with a daylight saving transition 23 or 25 hours long.
 */
public final class PublicationThreshold {

    private PublicationThreshold() {
    }

    /**
     * Whether {@code publishedHours} is at least the minimum share of {@code possibleHours}.
     */
    public static boolean isReached(long publishedHours, long possibleHours) {
        // Integer arithmetic, so the threshold is exact rather than subject to rounding.
        return publishedHours * 100 >= possibleHours * ReferenceMonthResolver.PUBLISHED_HOURS_MIN_PERCENT;
    }
}
