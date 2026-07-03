package org.lucoenergia.conluz.domain.production.datadis.aggregate;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.time.Month;
import java.util.UUID;

/**
 * Service for aggregating Datadis hourly production data into monthly totals.
 */
public interface DatadisProductionMonthlyAggregationService {

    /**
     * Aggregates hourly production data into monthly totals for all supplies
     * for a specific month and year.
     *
     * @param month the month to aggregate
     * @param year the year to aggregate
     */
    void aggregateMonthlyProductions(Month month, int year);

    // --- Community-scoped variants: only the given community's supplies are aggregated ---

    void aggregateMonthlyProductions(UUID communityId, int year);

    void aggregateMonthlyProductions(UUID communityId, Month month, int year);

    /**
     * Aggregates a single supply, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's supply.
     */
    void aggregateMonthlyProductions(UUID communityId, SupplyCode supplyCode, Month month, int year);

    /**
     * Entry point for the manual community sync endpoint. Verifies that Datadis is enabled for the
     * community and then dispatches to the appropriate aggregation depending on whether a specific
     * supply and/or month were requested. All the sync orchestration (config gating and dispatch)
     * lives here so the controller does not embed domain logic.
     *
     * @param communityId the community whose supplies are aggregated
     * @param supplyCode  optional CUPS; when null/blank, all the community's supplies are aggregated
     * @param month       optional month (1-12); when null, every month of the year is aggregated
     * @param year        the year to aggregate
     */
    void syncMonthlyProductions(UUID communityId, String supplyCode, Integer month, int year);
}
