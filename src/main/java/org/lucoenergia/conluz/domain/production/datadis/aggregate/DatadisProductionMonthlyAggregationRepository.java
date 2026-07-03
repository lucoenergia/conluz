package org.lucoenergia.conluz.domain.production.datadis.aggregate;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.Month;

/**
 * Repository for aggregating hourly Datadis production data into monthly totals
 * using InfluxDB aggregation queries.
 */
public interface DatadisProductionMonthlyAggregationRepository {

    /**
     * Aggregates hourly production data into a monthly total for a specific supply,
     * month, and year using InfluxQL SUM queries.
     *
     * @param supply the supply to aggregate
     * @param month the month to aggregate
     * @param year the year to aggregate
     */
    void aggregateMonthlyProduction(@NotNull Supply supply, @NotNull Month month, @NotNull int year);
}
