package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.util.Collections;
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
        this.segments = Collections.unmodifiableList(segments);
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
