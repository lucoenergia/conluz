package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What was observed of one coefficient row.
 *
 * @param storedValidFrom the stored start as a local day, or null when pending
 * @param storedValidTo   the stored exclusive end as a local day, or null when open
 * @param observedDate    the observed application date, or null when not observed
 * @param proposedDate    for {@link ObservationStatus#NOT_DISTINGUISHABLE}, the agreement's most frequent observed
 *                        date; otherwise null
 * @param observedBeta    what the supply received over its row window (OBS-009)
 * @param windowDays      the plant-valid days in the row window, 0 when it has none
 */
public record CoefficientObservation(UUID coefficientId, ObservedSupply supply, BigDecimal coefficient,
                                     LocalDate storedValidFrom, LocalDate storedValidTo, ObservationStatus status,
                                     ObservedDate observedDate, ObservedDate proposedDate, ObservedBeta observedBeta,
                                     int windowDays) {
}
