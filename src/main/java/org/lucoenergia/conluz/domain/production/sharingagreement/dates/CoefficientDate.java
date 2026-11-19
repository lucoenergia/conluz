package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The first day one coefficient of the agreement applies: it activates a pending coefficient or corrects an active one.
 */
public record CoefficientDate(UUID coefficientId, LocalDate validFrom) {

    public CoefficientDate {
        Objects.requireNonNull(coefficientId, "coefficientId");
        Objects.requireNonNull(validFrom, "validFrom");
    }
}
