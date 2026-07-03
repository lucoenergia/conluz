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
}
