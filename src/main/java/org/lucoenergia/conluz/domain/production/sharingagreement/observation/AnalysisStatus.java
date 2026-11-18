package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.LocalDate;
import java.util.List;

/**
 * What the observation of a plant could analyse.
 *
 * @param notComputableReason null when computable
 * @param analysedFrom        null when not computable
 * @param analysedTo          null when not computable
 */
public record AnalysisStatus(boolean computable, NotComputableReason notComputableReason, LocalDate analysedFrom,
                             LocalDate analysedTo, List<ObservedSupply> suppliesWithData,
                             List<ObservedSupply> suppliesWithoutData, List<ObservedSupply> notComputableSupplies) {
}
