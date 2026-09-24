package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;

import java.time.Month;
import java.time.OffsetDateTime;
import java.util.List;

public interface GetDatadisConsumptionRepository {

    List<DatadisConsumption> getHourlyConsumptionsByMonth(@NotNull Supply supply, @NotNull Month month, @NotNull int year);

    /**
     * Retrieves a list of daily consumption data within a specified date range for the given supply.
     *
     * <p>Days are the local calendar days of the supply's time zone, so a day lasts 23 or 25 hours
     * across a daylight saving transition. The bounds are used as given, without being rounded to a
     * day boundary, so bounds that fall mid-day yield a partial first and last day. A day with no
     * stored record is returned with zero energy rather than omitted.
     *
     * @param supply the supply for which the consumption data is retrieved, must not be null
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     * @return a list of {@code DatadisConsumption} objects representing daily consumption data within the specified date range
     */
    List<DatadisConsumption> getDailyConsumptionsByRangeOfDates(@NotNull Supply supply,
                                                                @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves a list of hourly consumption data within a specified date range for the given supply.
     *
     * @param supply the supply for which the consumption data is retrieved, must not be null
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     * @return a list of {@code DatadisConsumption} objects representing hourly consumption data within the specified date range
     */
    List<DatadisConsumption> getHourlyConsumptionsByRangeOfDates(@NotNull Supply supply,
                                                                 @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves a list of monthly consumption data within a specified date range for the given supply.
     *
     * <p>Reads the pre-aggregated monthly measurement, whose points are stamped at local midnight on
     * the first day of each local calendar month and total that month as the supply's calendar sees
     * it. The bounds select those points by instant, so a bound expressed in UTC can select one month
     * too few or too many.
     *
     * @param supply the supply for which the consumption data is retrieved, must not be null
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     * @return a list of {@code DatadisConsumption} objects representing monthly consumption data within the specified date range
     */
    List<DatadisConsumption> getMonthlyConsumptionsByRangeOfDates(@NotNull Supply supply,
                                                                   @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves a list of yearly consumption data within a specified date range for the given supply.
     *
     * <p>Reads the pre-aggregated yearly measurement, whose points are stamped at local midnight on
     * January 1st of each local calendar year and total that year as the supply's calendar sees it.
     * The bounds select those points by instant, so a bound expressed in UTC can select one year too
     * few or too many.
     *
     * @param supply the supply for which the consumption data is retrieved, must not be null
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     * @return a list of {@code DatadisConsumption} objects representing yearly consumption data within the specified date range
     */
    List<DatadisConsumption> getYearlyConsumptionsByRangeOfDates(@NotNull Supply supply,
                                                                  @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);
}
