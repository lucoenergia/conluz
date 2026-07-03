package org.lucoenergia.conluz.domain.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.util.UUID;

/**
 * Service for aggregating Datadis monthly consumption data into yearly totals.
 * Uses InfluxQL queries to aggregate data directly in the database.
 */
public interface DatadisYearlyAggregationService {

    /**
     * Aggregates monthly consumption data into yearly totals for all supplies
     * for a specific year.
     *
     * @param year the year to aggregate
     */
    void aggregateYearlyConsumptions(int year);

    // --- Community-scoped variants: only the given community's supplies are aggregated ---

    void aggregateYearlyConsumptions(UUID communityId, int year);

    /**
     * Aggregates a single supply, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's supply.
     */
    void aggregateYearlyConsumptions(UUID communityId, SupplyCode supplyCode, int year);

    /**
     * Entry point for the manual community sync endpoint. Verifies that Datadis is enabled for the
     * community and then dispatches to the appropriate aggregation depending on whether a specific
     * supply was requested.
     *
     * @param communityId the community whose supplies are aggregated
     * @param supplyCode  optional CUPS; when null/blank, all the community's supplies are aggregated
     * @param year        the year to aggregate
     */
    void syncYearlyConsumptions(UUID communityId, String supplyCode, int year);
}
