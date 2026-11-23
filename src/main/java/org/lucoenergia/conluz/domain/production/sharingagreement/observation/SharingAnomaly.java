package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A sustained disagreement between what the distributor applies to a supply and the registered agreements.
 *
 * @param id                   derived from the supply, the type and the start day, so it is stable across calls
 * @param start                when it started
 * @param end                  its last day, or null when it lasts until the end of the data
 * @param attributedAgreementId the agreement it is attributed to, or null
 * @param observedBeta         what the supply received over the anomaly's valid days
 * @param expectedBeta         the supply's registered value in the attributed agreement, 0 when it holds no row
 *                             there, null when unattributed
 */
public record SharingAnomaly(UUID id, ObservedSupply supply, SharingAnomalyType type, ObservedDate start,
                             LocalDate end, UUID attributedAgreementId, ObservedBeta observedBeta,
                             BigDecimal expectedBeta) {
}
