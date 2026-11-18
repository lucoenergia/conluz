package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.LocalDate;

/**
 * A consumer supply's Datadis totals over one local day.
 *
 * @param energyKWh      self-consumption plus surplus: the share of the plant's production it received
 * @param publishedHours the number of its records carrying self-consumption, zeros included
 */
public record SupplyDailyEnergy(LocalDate day, double energyKWh, long publishedHours) {
}
