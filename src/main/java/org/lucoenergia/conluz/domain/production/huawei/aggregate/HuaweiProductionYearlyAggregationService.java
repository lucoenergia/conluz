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

    // --- Community-scoped variants: only the given community's plants are aggregated ---

    void aggregateYearlyProductions(UUID communityId, int year);

    /**
     * Aggregates a single plant, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's plant.
     */
    void aggregateYearlyProductions(UUID communityId, String plantCode, int year);

    /**
     * Entry point for the manual community sync endpoint. Verifies that Huawei is enabled and then
     * dispatches to the appropriate aggregation depending on whether a specific plant was requested.
     * All the sync orchestration (config gating and dispatch) lives here so the controller does not
     * embed domain logic.
     *
     * @param communityId the community whose plants are aggregated
     * @param plantCode   optional plant code; when null/blank, all the community's plants are aggregated
     * @param year        the year to aggregate
     */
    void syncYearlyProductions(UUID communityId, String plantCode, int year);
}
