package org.lucoenergia.conluz.domain.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.time.Month;
import java.util.UUID;

/**
 * Service for aggregating Datadis hourly consumption data into monthly totals.
 * Uses InfluxQL queries to aggregate data directly in the database.
 */
public interface DatadisMonthlyAggregationService {

    /**
     * Aggregates hourly consumption data into monthly totals for all supplies
     * for a specific month and year.
     *
     * @param month the month to aggregate
     * @param year the year to aggregate
     */
    void aggregateMonthlyConsumptions(Month month, int year);

    // --- Community-scoped variants: only the given community's supplies are aggregated ---

    void aggregateMonthlyConsumptions(UUID communityId, int year);

    void aggregateMonthlyConsumptions(UUID communityId, Month month, int year);

    /**
     * Aggregates a single supply, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's supply.
     */
    void aggregateMonthlyConsumptions(UUID communityId, SupplyCode supplyCode, Month month, int year);
}
