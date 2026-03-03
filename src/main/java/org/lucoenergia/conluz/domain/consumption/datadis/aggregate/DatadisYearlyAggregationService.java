package org.lucoenergia.conluz.domain.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

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

    /**
     * Aggregates monthly consumption data into yearly totals for a specific supply
     * and year.
     *
     * @param supplyCode the supply code to aggregate
     * @param year the year to aggregate
     */
    void aggregateYearlyConsumptions(SupplyCode supplyCode, int year);
}
