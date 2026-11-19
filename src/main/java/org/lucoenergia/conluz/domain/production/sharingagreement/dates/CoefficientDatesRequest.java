package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import java.util.List;

/**
 * The dates set on one agreement in one request: a start per coefficient and a closing date per leaving supply.
 */
public record CoefficientDatesRequest(List<CoefficientDate> coefficients, List<LeavingClosureDate> leavingClosures) {

    public CoefficientDatesRequest {
        coefficients = List.copyOf(coefficients);
        leavingClosures = List.copyOf(leavingClosures);
    }
}
