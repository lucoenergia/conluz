package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.LocalDate;

/**
 * The plant supply's Datadis totals over one local day.
 *
 * @param productionKWh  the plant supply's surplus: the energy the distributor shares
 * @param hours          the number of the plant supply's records the day's energy rests on, zeros included
 */
public record PlantDailyEnergy(LocalDate day, double productionKWh, long hours) {
}
