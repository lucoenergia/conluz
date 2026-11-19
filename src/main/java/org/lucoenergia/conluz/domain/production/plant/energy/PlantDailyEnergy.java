package org.lucoenergia.conluz.domain.production.plant.energy;

import java.time.LocalDate;

/**
 * A plant supply's energy over one local day, read from a single source.
 *
 * @param productionKWh the plant supply's production: the energy the distributor shares
 * @param hours         the number of the source's records the energy rests on, zeros included
 * @param source        where the day's energy and hours were read from
 */
public record PlantDailyEnergy(LocalDate day, double productionKWh, long hours, PlantEnergySource source) {
}
