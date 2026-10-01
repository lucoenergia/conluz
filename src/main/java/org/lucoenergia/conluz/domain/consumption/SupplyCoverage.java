package org.lucoenergia.conluz.domain.consumption;

/**
 * How much of a period one supply's stored hourly records cover.
 */
public class SupplyCoverage {

    private final long hoursWithData;
    private final long expectedHours;

    /**
     * @param hoursWithData the number of hourly consumption records the supply stored in the period
     * @param expectedHours the number of hours the period spans
     */
    public SupplyCoverage(long hoursWithData, long expectedHours) {
        this.hoursWithData = hoursWithData;
        this.expectedHours = expectedHours;
    }

    public long getHoursWithData() {
        return hoursWithData;
    }

    public long getExpectedHours() {
        return expectedHours;
    }
}
