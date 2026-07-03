package org.lucoenergia.conluz.domain.production.datadis.aggregate;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.util.UUID;

/**
 * Service for aggregating Datadis monthly production data into yearly totals.
 */
public interface DatadisProductionYearlyAggregationService {

    /**
     * Aggregates monthly production data into yearly totals for all supplies
     * for a specific year.
     *
     * @param year the year to aggregate
     */
    void aggregateYearlyProductions(int year);

    // --- Community-scoped variants: only the given community's supplies are aggregated ---

    void aggregateYearlyProductions(UUID communityId, int year);

    /**
     * Aggregates a single supply, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's supply.
     */
    void aggregateYearlyProductions(UUID communityId, SupplyCode supplyCode, int year);
}
