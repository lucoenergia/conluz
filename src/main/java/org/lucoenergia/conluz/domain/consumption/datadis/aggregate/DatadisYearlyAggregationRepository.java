package org.lucoenergia.conluz.domain.consumption.datadis.aggregate;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

/**
 * Repository for aggregating monthly Datadis consumption data into yearly totals
 * using InfluxDB aggregation queries.
 */
public interface DatadisYearlyAggregationRepository {

    /**
     * Aggregates monthly consumption data into a yearly total for a specific supply
     * and year using InfluxQL SUM queries.
     *
     * @param supply the supply to aggregate
     * @param year the year to aggregate
     */
    void aggregateYearlyConsumption(@NotNull Supply supply, @NotNull int year);
}
