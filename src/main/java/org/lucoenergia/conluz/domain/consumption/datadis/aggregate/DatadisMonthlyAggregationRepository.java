package org.lucoenergia.conluz.domain.consumption.datadis.aggregate;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.Month;

/**
 * Repository for aggregating hourly Datadis consumption data into monthly totals
 * using InfluxDB aggregation queries.
 */
public interface DatadisMonthlyAggregationRepository {

    /**
     * Aggregates hourly consumption data into a monthly total for a specific supply,
     * month, and year using InfluxQL SUM queries.
     *
     * @param supply the supply to aggregate
     * @param month the month to aggregate
     * @param year the year to aggregate
     */
    void aggregateMonthlyConsumption(@NotNull Supply supply, @NotNull Month month, @NotNull int year);
}
