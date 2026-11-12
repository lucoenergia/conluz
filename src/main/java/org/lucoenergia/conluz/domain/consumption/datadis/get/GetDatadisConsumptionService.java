package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplyHourlyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for retrieving Datadis consumption data.
 */
public interface GetDatadisConsumptionService {

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
     * Retrieves the monthly consumption series for a supply, summed from its hourly records.
     *
     * <p>A month is selected when its local midnight on day 1 falls inside the period, clipped to
     * the start of the current hour, and returned only when the supply's records carrying
     * self-consumption reach the publication threshold of the month's hours inside the period: a
     * month whose self-consumption is not yet published is omitted rather than reported as zero.
     * Each month totals, and is priced over, its hours inside the period.
     *
     * @param supplyId the supply ID for which to retrieve consumption data
     * @param startDate the start of the period, inclusive
     * @param endDate the end of the period, exclusive
     * @return the months with published data, in chronological order, with their estimated savings
     */
    List<SupplyConsumptionBucket> getMonthlySeriesBySupply(@NotNull SupplyId supplyId,
                                                            @NotNull OffsetDateTime startDate,
                                                            @NotNull OffsetDateTime endDate);

    /**
     * Retrieves the hourly consumption series for a supply over a half-open period, each hour priced
     * over itself.
     *
     * <p>The period obeys the same rules as the daily series' and is clipped the same way, with a
     * cap of its own: {@code EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH}. One bucket is returned
     * for every hour that starts inside the clipped period, in chronological order, whether or not
     * the supply stored a record for it: an hour without one comes with zero energy, no obtain
     * method and nothing saved. Each hour is priced with the same tariff resolution as a day of the
     * daily series, over the hour clipped to the period's end.
     *
     * @param supplyId the supply ID for which to retrieve consumption data
     * @param startDate the start of the period, inclusive
     * @param endDate the end of the period, exclusive
     * @return every hour of the period, each with the instant it starts at and its estimated savings
     */
    List<SupplyHourlyConsumptionBucket> getHourlySeriesBySupply(@NotNull SupplyId supplyId,
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
