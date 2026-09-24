package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Immutable aggregate describing the tariff applicable to a supply point across
 * a period of time, expressed as an ordered list of {@link TariffSegment}s.
 *
 * <p>Each segment covers a sub-range with its own plan and VAT, so a schedule
 * captures how a supply's pricing evolves over time (e.g. a tariff change
 * mid-period). It is the result returned by {@link SupplyTariffResolver}.
 */
public class TariffSchedule {

    private final List<TariffSegment> segments;

    public TariffSchedule(List<TariffSegment> segments) {
        if (segments == null) {
            throw new InvalidTariffScheduleException(InvalidTariffScheduleException.Reason.NULL_SEGMENTS);
        }
        if (segments.isEmpty()) {
            throw new InvalidTariffScheduleException(InvalidTariffScheduleException.Reason.EMPTY);
        }
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i) == null) {
                throw new InvalidTariffScheduleException(
                        InvalidTariffScheduleException.Reason.NULL_SEGMENT, i);
            }
        }
        for (int i = 1; i < segments.size(); i++) {
            assertContiguous(segments.get(i - 1), segments.get(i), i);
        }
        // Defensive copy, not Collections.unmodifiableList: the latter is a read-only *view*
        // over the caller's list, so the caller could still mutate the schedule after the
        // invariants above were checked.
        this.segments = List.copyOf(segments);
    }

    /**
     * Checks that {@code current} resumes exactly where {@code previous} stopped. The three
     * failures are distinguished rather than collapsed into one "bad boundary": a gap means
     * unpriced energy, an overlap means energy priced twice, and being out of order means the
     * producer is not emitting a timeline at all.
     */
    private static void assertContiguous(TariffSegment previous, TariffSegment current, int index) {
        LocalDate previousEnd = previous.getRange().getEnd();
        LocalDate currentStart = current.getRange().getStart();

        if (currentStart.isBefore(previous.getRange().getStart())) {
            throw new InvalidTariffScheduleException(
                    InvalidTariffScheduleException.Reason.OUT_OF_ORDER, index);
        }
        if (currentStart.isBefore(previousEnd)) {
            throw new InvalidTariffScheduleException(
                    InvalidTariffScheduleException.Reason.OVERLAPPING, index);
        }
        if (currentStart.isAfter(previousEnd)) {
            throw new InvalidTariffScheduleException(
                    InvalidTariffScheduleException.Reason.GAP, index);
        }
    }

    public List<TariffSegment> getSegments() {
        return segments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TariffSchedule)) return false;
        TariffSchedule that = (TariffSchedule) o;
        return Objects.equals(segments, that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(segments);
    }

    @Override
    public String toString() {
        return "TariffSchedule{" +
                "segments=" + segments +
                '}';
    }
}
