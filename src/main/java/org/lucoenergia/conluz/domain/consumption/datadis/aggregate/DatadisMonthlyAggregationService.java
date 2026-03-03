package org.lucoenergia.conluz.domain.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.time.Month;

/**
 * Service for aggregating Datadis hourly consumption data into monthly totals.
 * Uses InfluxQL queries to aggregate data directly in the database.
 */
public interface DatadisMonthlyAggregationService {

    /**
     * Aggregates hourly consumption data into monthly totals for all supplies
     * for a specific year.
     *
     * @param year the year to aggregate
     */
    void aggregateMonthlyConsumptions(int year);

    /**
     * Aggregates hourly consumption data into monthly totals for a specific supply,
     * month, and year.
     *
     * @param supplyCode the supply code to aggregate
     * @param month the month to aggregate
     * @param year the year to aggregate
     */
    void aggregateMonthlyConsumptions(SupplyCode supplyCode, Month month, int year);

    /**
     * Aggregates hourly consumption data into monthly totals for all supplies
     * for a specific month and year.
     *
     * @param month the month to aggregate
     * @param year the year to aggregate
     */
    void aggregateMonthlyConsumptions(Month month, int year);
}
