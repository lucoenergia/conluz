package org.lucoenergia.conluz.domain.production.datadis.get;

import java.time.LocalDate;

/**
 * Totals of a supply's hourly production records over one local day holding at least one of them.
 *
 * @param day           the local day, in the zone the records were grouped in
 * @param productionKWh the stored production summed, unrounded; 0 when every record carries 0
 * @param hoursWithData the number of records, zeros included
 */
public record DailyProductionAggregate(LocalDate day, double productionKWh, long hoursWithData) {
}
