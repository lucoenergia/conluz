package org.lucoenergia.conluz.domain.production.datadis.aggregate;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.util.UUID;

/**
 * Service for aggregating Datadis monthly production data into yearly totals.
 */
public interface DatadisProductionYearlyAggregationService {

    /**
     * Aggregates the year for the community's supplies linked to a plant.
     */
    void aggregateYearlyProductions(UUID communityId, int year);

    /**
     * Aggregates a single supply, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's supply, and to be linked to a plant of that
     * community; otherwise it throws {@code SupplyNotFoundException}.
     */
    void aggregateYearlyProductions(UUID communityId, SupplyCode supplyCode, int year);

    /**
     * Entry point for the manual community sync endpoint. Verifies that Datadis is enabled for the
     * community and then dispatches to the appropriate aggregation depending on whether a specific
     * supply was requested. All the sync orchestration (config gating and dispatch) lives here so
     * the controller does not embed domain logic.
     *
     * @param communityId the community whose supplies are aggregated
     * @param supplyCode  optional CUPS; when null/blank, all the community's supplies linked to a plant are aggregated
     * @param year        the year to aggregate
     */
    void syncYearlyProductions(UUID communityId, String supplyCode, int year);
}
