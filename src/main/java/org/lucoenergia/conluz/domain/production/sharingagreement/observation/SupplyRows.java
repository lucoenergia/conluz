package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One supply's rows in each agreement, keyed by agreement id: its coefficient rows, and the leaving row of an
 * agreement it is absent from, when it has one.
 */
record SupplyRows(Map<UUID, List<CoefficientObservation>> coefficients, Map<UUID, LeavingObservation> leavings) {
}
