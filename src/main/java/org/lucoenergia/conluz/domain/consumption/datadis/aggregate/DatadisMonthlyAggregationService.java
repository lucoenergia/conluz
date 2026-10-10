package org.lucoenergia.conluz.domain.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.time.Month;
import java.util.UUID;

/**
 * Service for aggregating Datadis hourly consumption data into monthly totals.
 * Uses InfluxQL queries to aggregate data directly in the database.
 */
public interface DatadisMonthlyAggregationService {

    void aggregateMonthlyConsumptions(UUID communityId, int year);

    void aggregateMonthlyConsumptions(UUID communityId, Month month, int year);

    /**
     * Aggregates a single supply, requiring it to belong to the given community so a job for one
     * community cannot aggregate another community's supply.
     */
    void aggregateMonthlyConsumptions(UUID communityId, SupplyCode supplyCode, Month month, int year);

    /**
     * Entry point for the manual community sync endpoint. Verifies that Datadis is enabled for the
     * community and then dispatches to the appropriate aggregation depending on whether a specific
     * supply and/or month were requested.
     *
     * @param communityId the community whose supplies are aggregated
     * @param supplyCode  optional CUPS; when null/blank, all the community's supplies are aggregated
     * @param month       optional month (1-12); when null, every month of the year is aggregated
     * @param year        the year to aggregate
     */
    void syncMonthlyConsumptions(UUID communityId, String supplyCode, Integer month, int year);
}
