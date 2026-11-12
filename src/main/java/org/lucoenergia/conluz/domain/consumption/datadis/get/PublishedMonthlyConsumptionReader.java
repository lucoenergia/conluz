package org.lucoenergia.conluz.domain.consumption.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the monthly consumption buckets of supplies from their hourly records, keeping only the
 * months in which each supply's self-consumption is published.
 */
public interface PublishedMonthlyConsumptionReader {

    /**
     * Every month whose local midnight on day 1, in {@code zone}, falls inside {@code period}, for each
     * supply that has data in it: its records carrying self-consumption reach the publication threshold
     * of the month's hours inside the period. Each bucket totals the supply's records of the month
     * inside the period and is priced over that same interval; it is labelled with the month's first
     * day at midnight.
     *
     * <p>Every supply's records are read in a single query, however many supplies and months there
     * are.
     *
     * @return the buckets of each supply, keyed by supply id, in chronological order; a supply without
     * any published month is absent
     */
    Map<UUID, List<SupplyConsumptionBucket>> getPublishedMonths(List<Supply> supplies, EnergyMetricsPeriod period,
                                                                ZoneId zone);
}
