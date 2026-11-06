package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import java.time.LocalDate;

/**
 * Totals of a supply's hourly consumption records over one local day.
 *
 * @param day                the local day, in the zone the records were grouped in
 * @param selfConsumptionKWh the stored self-consumption summed, 0 when no record carries it
 * @param surplusKWh         the stored surplus summed, 0 when no record carries it
 * @param publishedHours     the number of records carrying self-consumption, zero included
 */
public record DailyPublishedAggregate(LocalDate day, double selfConsumptionKWh, double surplusKWh,
                                      long publishedHours) {
}
