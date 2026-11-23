package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A stored coefficient row as the observation reads it. A supply holds at most one row in an agreement (#601).
 *
 * @param validFrom the stored start, or null when pending
 * @param validTo   the stored exclusive end, or null when open
 */
public record ObservedCoefficient(UUID id, UUID supplyId, UUID sharingAgreementId, BigDecimal coefficient,
                                  Instant validFrom, Instant validTo) {
}
