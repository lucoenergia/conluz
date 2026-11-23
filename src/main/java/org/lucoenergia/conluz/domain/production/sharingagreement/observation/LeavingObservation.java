package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A supply leaving the distribution in an agreement it is absent from, after the previous agreement was
 * observed applied for it (OBS-008).
 *
 * @param previousAgreement     the agreement observed applied before, where the supply had a non-zero value
 * @param previousCoefficientId the supply's row in it
 * @param previousCoefficient   its value there
 * @param observedLeavingDate   the observed leaving date, or null when not observed
 * @param storedValidTo         the stored exclusive end of the previous row as a local day, or null when open
 * @param proposedDate          for {@link ObservationStatus#NOT_DISTINGUISHABLE}, the agreement's most frequent
 *                              observed date; otherwise null
 * @param observedBeta          what the supply received over its row window (OBS-009)
 * @param windowDays            the plant-valid days in the row window, 0 when it has none
 * @param windowFrom            the first local day of the row window, or null when it has none
 * @param windowTo              the last local day of the row window, or null when it has none
 */
public record LeavingObservation(ObservedSupply supply, ObservedAgreement previousAgreement,
                                 UUID previousCoefficientId, BigDecimal previousCoefficient,
                                 ObservedDate observedLeavingDate, LocalDate storedValidTo,
                                 ObservationStatus status, ObservedDate proposedDate,
                                 ObservedBeta observedBeta, int windowDays, LocalDate windowFrom,
                                 LocalDate windowTo) {
}
