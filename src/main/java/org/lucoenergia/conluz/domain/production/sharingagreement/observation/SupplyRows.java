package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.util.Map;
import java.util.UUID;

/**
 * One supply's rows in each agreement, keyed by agreement id: its coefficient row, and the leaving row of an
 * agreement it is absent from, when it has one.
 */
record SupplyRows(Map<UUID, CoefficientObservation> coefficients, Map<UUID, LeavingObservation> leavings) {
}
