package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import java.time.LocalDate;

/**
 * Totals of a supply's hourly consumption records over one local day.
 *
 * @param day                the local day, in the zone the records were grouped in
 * @param consumptionKWh     the stored grid import summed, 0 when no record carries it
 * @param selfConsumptionKWh the stored self-consumption summed, 0 when no record carries it
 * @param surplusKWh         the stored surplus summed, 0 when no record carries it
 * @param publishedHours     the number of records carrying self-consumption, zero included
 * @param obtainMethod       the obtain method of the day's last record, or null when no record carries one
 */
public record DailyPublishedAggregate(LocalDate day, double consumptionKWh, double selfConsumptionKWh,
                                      double surplusKWh, long publishedHours, String obtainMethod) {
}
