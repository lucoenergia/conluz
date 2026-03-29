package org.lucoenergia.conluz.domain.production.huawei.aggregate;

import java.time.Month;

/**
 * Service for aggregating Huawei hourly production data into monthly totals.
 * Uses InfluxQL queries to aggregate data directly in the database.
 */
public interface HuaweiProductionMonthlyAggregationService {

    /**
     * Aggregates hourly production data into monthly totals for all plants
     * for all months of a specific year.
     *
     * @param year the year to aggregate
     */
    void aggregateMonthlyProductions(int year);

    /**
     * Aggregates hourly production data into monthly totals for all plants
     * for a specific month and year.
     *
     * @param month the month to aggregate
     * @param year  the year to aggregate
     */
    void aggregateMonthlyProductions(Month month, int year);

    /**
     * Aggregates hourly production data into monthly totals for a specific plant
     * and month/year combination.
     *
     * @param plantCode the plant code to aggregate
     * @param month     the month to aggregate
     * @param year      the year to aggregate
     */
    void aggregateMonthlyProductions(String plantCode, Month month, int year);
}
