package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the observation of a plant is computed from, already read from the stores.
 *
 * @param zone                   the zone the local days are read in
 * @param peakPowerKWp           the plant's peak power
 * @param plantSupplyId          the plant's own supply, never analysed as a consumer
 * @param analysedFrom           the first local day analysed, or null when the plant supply has no data
 * @param analysedTo             the last local day analysed, or null when the plant supply has no data
 * @param plantDays              the plant supply's days inside the range
 * @param supplies               the consumer supplies analysed
 * @param supplyDays             each analysed supply's days inside the range, keyed by supply id
 * @param notComputableSupplies  supplies whose energy cannot be attributed to this plant alone
 * @param agreements             the plant's agreements, DRAFT ones included; they are filtered here
 * @param coefficients           every coefficient row of the plant, of every agreement
 */
public record ObservationInput(ZoneId zone, double peakPowerKWp, UUID plantSupplyId, LocalDate analysedFrom,
                               LocalDate analysedTo, List<PlantDailyEnergy> plantDays,
                               List<ObservedSupply> supplies, Map<UUID, List<SupplyDailyEnergy>> supplyDays,
                               List<ObservedSupply> notComputableSupplies, List<ObservedAgreement> agreements,
                               List<ObservedCoefficient> coefficients) {
}
