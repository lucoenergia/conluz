package org.lucoenergia.conluz.domain.production.datadis.get;

/**
 * Totals of a supply's hourly production records over an interval holding at least one of them.
 *
 * @param productionKWh the stored production summed, unrounded; 0 when every record carries 0
 * @param hoursWithData the number of records, zeros included
 */
public record ProductionAggregate(double productionKWh, long hoursWithData) {
}
