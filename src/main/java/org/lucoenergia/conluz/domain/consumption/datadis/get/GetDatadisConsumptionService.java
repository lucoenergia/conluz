package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for retrieving Datadis consumption data.
 */
public interface GetDatadisConsumptionService {

    /**
     * Retrieves daily consumption data for a specific supply within a date range.
     * Access is restricted based on user role and ownership.
     *
     * @param supplyId the supply ID for which to retrieve consumption data
     * @param startDate the start date of the range, inclusive
     * @param endDate the end date of the range, inclusive
     * @return a list of daily consumption data
     */
    List<DatadisConsumption> getDailyConsumptionBySupply(@NotNull SupplyId supplyId,
                                                          @NotNull OffsetDateTime startDate,
                                                          @NotNull OffsetDateTime endDate);

    /**
     * Retrieves the daily consumption series for a supply, each bucket priced over its own local
     * calendar day.
     *
     * <p>A bucket's interval is the local day its label names, intersected with the requested
     * range: the underlying query reads the hourly measurement and is itself bounded by the
     * request, so bounds falling mid-day yield a partial bucket whose savings cover exactly the
     * hours its energy fields cover.
     *
     * @param supplyId the supply ID for which to retrieve consumption data
     * @param startDate the start date of the range, inclusive
     * @param endDate the end date of the range, inclusive
     * @return a list of daily consumption buckets with their estimated savings
     */
    List<SupplyConsumptionBucket> getDailySeriesBySupply(@NotNull SupplyId supplyId,
                                                          @NotNull OffsetDateTime startDate,
                                                          @NotNull OffsetDateTime endDate);

    /**
     * Retrieves the monthly consumption series for a supply, each bucket priced over its
     * <strong>whole</strong> local calendar month.
     *
     * <p>The interval is deliberately <em>not</em> intersected with the requested range, unlike the
     * daily series. A monthly bucket is a pre-aggregate stamped at local midnight of day 1: the
     * range selects it by that single instant, and once selected it carries the entire month's
     * energy whatever the bounds are. Pricing a clipped interval would value a fraction of the
     * month against the whole month's energy.
     *
     * @param supplyId the supply ID for which to retrieve consumption data
     * @param startDate the start date of the range, inclusive
     * @param endDate the end date of the range, inclusive
     * @return a list of monthly consumption buckets with their estimated savings
     */
    List<SupplyConsumptionBucket> getMonthlySeriesBySupply(@NotNull SupplyId supplyId,
                                                            @NotNull OffsetDateTime startDate,
                                                            @NotNull OffsetDateTime endDate);

    /**
     * Retrieves hourly consumption data for a specific supply within a date range.
     * Access is restricted based on user role and ownership.
     *
     * @param supplyId the supply ID for which to retrieve consumption data
     * @param startDate the start date of the range, inclusive
     * @param endDate the end date of the range, inclusive
     * @return a list of hourly consumption data
     */
    List<DatadisConsumption> getHourlyConsumptionBySupply(@NotNull SupplyId supplyId,
                                                           @NotNull OffsetDateTime startDate,
                                                           @NotNull OffsetDateTime endDate);

    /**
     * Retrieves monthly consumption data for a specific supply within a date range.
     * Access is restricted based on user role and ownership.
     *
     * @param supplyId the supply ID for which to retrieve consumption data
     * @param startDate the start date of the range, inclusive
     * @param endDate the end date of the range, inclusive
     * @return a list of monthly consumption data
     */
    List<DatadisConsumption> getMonthlyConsumptionBySupply(@NotNull SupplyId supplyId,
                                                            @NotNull OffsetDateTime startDate,
                                                            @NotNull OffsetDateTime endDate);

    /**
     * Retrieves yearly consumption data for a specific supply within a date range.
     * Access is restricted based on user role and ownership.
     *
     * @param supplyId the supply ID for which to retrieve consumption data
     * @param startDate the start date of the range, inclusive
     * @param endDate the end date of the range, inclusive
     * @return a list of yearly consumption data
     */
    List<DatadisConsumption> getYearlyConsumptionBySupply(@NotNull SupplyId supplyId,
                                                           @NotNull OffsetDateTime startDate,
                                                           @NotNull OffsetDateTime endDate);
}
