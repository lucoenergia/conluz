package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The first day a supply absent from the agreement no longer receives production through the coefficient it had
 * before the agreement started.
 */
public record LeavingClosureDate(UUID supplyId, LocalDate closedOn) {

    public LeavingClosureDate {
        Objects.requireNonNull(supplyId, "supplyId");
        Objects.requireNonNull(closedOn, "closedOn");
    }
}
