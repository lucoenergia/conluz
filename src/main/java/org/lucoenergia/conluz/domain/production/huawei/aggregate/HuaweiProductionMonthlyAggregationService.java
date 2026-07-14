package org.lucoenergia.conluz.domain.production.huawei.aggregate;

import java.time.Month;
import java.util.UUID;

/**
 * Service for aggregating Huawei hourly production data into monthly totals.
 * Uses InfluxQL queries to aggregate data directly in the database.
 */
public interface HuaweiProductionMonthlyAggregationService {

    /**
     * Aggregates hourly production data into monthly totals for all plants
     * for a specific month and year.
     *
     * @param month the month to aggregate
     * @param year  the year to aggregate
     */
    void aggregateMonthlyProductions(Month month, int year);

    // --- Community-scoped variants: only the given community's plants are aggregated ---

    void aggregateMonthlyProductions(UUID communityId, int year);

    void aggregateMonthlyProductions(UUID communityId, Month month, int year);

    /**
     * Aggregates a single plant, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's plant.
     */
    void aggregateMonthlyProductions(UUID communityId, String plantProviderCode, Month month, int year);

    /**
     * Entry point for the manual community sync endpoint. Verifies that Huawei is enabled and then
     * dispatches to the appropriate aggregation depending on whether a specific plant and/or month
     * were requested. All the sync orchestration (config gating and dispatch) lives here so the
     * controller does not embed domain logic.
     *
     * @param communityId the community whose plants are aggregated
     * @param plantProviderCode   optional plant code; when null/blank, all the community's plants are aggregated
     * @param month       optional month (1-12); when null, every month of the year is aggregated
     * @param year        the year to aggregate
     */
    void syncMonthlyProductions(UUID communityId, String plantProviderCode, Integer month, int year);
}
