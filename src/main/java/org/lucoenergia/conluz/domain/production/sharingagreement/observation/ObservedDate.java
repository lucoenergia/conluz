package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The local day something was observed to start, as precisely as the data allows: an exact day
 * ({@code from == to}), a range of days when non-valid or ambiguous days precede the first observed day,
 * or open ({@code from == null}), "on or before {@code to}", when the first observed day is the first valid
 * day of the data.
 *
 * @param from the earliest possible day, or null when open
 * @param to   the latest possible day: the first day the observation rests on
 */
public record ObservedDate(LocalDate from, LocalDate to) {

    public ObservedDate {
        Objects.requireNonNull(to, "to");
        if (from != null && from.isAfter(to)) {
            throw new IllegalArgumentException("An observed date cannot start after it ends");
        }
    }

    public static ObservedDate exact(LocalDate day) {
        return new ObservedDate(day, day);
    }

    public static ObservedDate onOrBefore(LocalDate day) {
        return new ObservedDate(null, day);
    }

    public boolean isOpen() {
        return from == null;
    }

    /**
     * Whether a stored date agrees with this observation: it is the exact day, lies within the range, or is
     * on or before {@code to} for an open date.
     */
    public boolean admits(LocalDate stored) {
        if (stored == null || stored.isAfter(to)) {
            return false;
        }
        return from == null || !stored.isBefore(from);
    }
}
