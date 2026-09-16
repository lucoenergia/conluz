package org.lucoenergia.conluz.domain.admin.supply.tariff;

/**
 * Raised when a {@link TariffSchedule} cannot be built from the segments supplied.
 *
 * <p>This signals a programming error in the {@link SupplyTariffResolver} implementation
 * producing the segments, not bad client input, so it is deliberately left unmapped to any
 * HTTP status. It carries the index of the offending segment so the caller's stack trace
 * is not the only clue about which boundary is wrong.
 */
public class InvalidTariffScheduleException extends RuntimeException {

    /**
     * Index of the segment the violation was detected at, or {@code -1} when the violation
     * concerns the list as a whole.
     */
    public static final int NO_INDEX = -1;

    public enum Reason {
        /**
         * The segment list itself was null.
         */
        NULL_SEGMENTS,
        /**
         * One of the segments was null.
         */
        NULL_SEGMENT,
        /**
         * The segment list was empty. A schedule that prices nothing cannot satisfy the
         * coverage postcondition of {@link SupplyTariffResolver#scheduleFor}; "no tariff
         * known" must be modelled as explicit absence by the consumer instead.
         */
        EMPTY,
        /**
         * A segment starts before its predecessor starts.
         */
        OUT_OF_ORDER,
        /**
         * A segment starts before its predecessor ends, so the two price the same day twice.
         */
        OVERLAPPING,
        /**
         * A segment starts after its predecessor ends, leaving days with no price at all.
         */
        GAP
    }

    private final Reason reason;
    private final int segmentIndex;

    public InvalidTariffScheduleException(Reason reason) {
        this(reason, NO_INDEX);
    }

    public InvalidTariffScheduleException(Reason reason, int segmentIndex) {
        this.reason = reason;
        this.segmentIndex = segmentIndex;
    }

    public Reason getReason() {
        return reason;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }
}
