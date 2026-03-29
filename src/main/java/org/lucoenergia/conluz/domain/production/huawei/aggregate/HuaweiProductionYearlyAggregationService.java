package org.lucoenergia.conluz.domain.production.huawei.aggregate;

/**
 * Service for aggregating Huawei monthly production data into yearly totals.
 * Uses InfluxQL queries to aggregate data directly in the database.
 */
public interface HuaweiProductionYearlyAggregationService {

    /**
     * Aggregates monthly production data into yearly totals for all plants
     * for a specific year.
     *
     * @param year the year to aggregate
     */
    void aggregateYearlyProductions(int year);

    /**
     * Aggregates monthly production data into yearly totals for a specific plant
     * and year.
     *
     * @param plantCode the plant code to aggregate
     * @param year      the year to aggregate
     */
    void aggregateYearlyProductions(String plantCode, int year);
}
