package org.lucoenergia.conluz.domain.production.huawei.aggregate;

import java.util.UUID;

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

    // --- Community-scoped variants: only the given community's plants are aggregated ---

    void aggregateYearlyProductions(UUID communityId, int year);

    /**
     * Aggregates a single plant, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's plant.
     */
    void aggregateYearlyProductions(UUID communityId, String plantCode, int year);
}
