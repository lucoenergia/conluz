package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisDailyConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisHourlyConsumption;

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
     * stored record is returned with zero energy rather than omitted. Each day comes with the number
     * of its records carrying self-consumption.
     *
     * @param supply the supply for which the consumption data is retrieved, must not be null
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, exclusive, must not be null
     * @return the days of the range, in chronological order
     */
    List<DatadisDailyConsumption> getDailyConsumptionsByRangeOfDates(@NotNull Supply supply,
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
     * Retrieves the supply's hourly consumption over a half-open period, each hour with the instant it
     * starts at.
     *
     * <p>An hour is returned for every whole hour the store groups between the bounds in which the
     * supply has at least one record anywhere in the period; an hour without a record of its own
     * comes back with zero energy and no obtain method. A period in which the supply has no record
     * at all returns nothing. Each hour comes with the number of its records carrying
     * self-consumption.
     *
     * @param supply the supply for which the consumption data is retrieved, must not be null
     * @param startDate the start of the period, inclusive, must not be null
     * @param endDate the end of the period, exclusive, must not be null
     * @return the hours of the period, in chronological order
     */
    List<DatadisHourlyConsumption> getHourlyConsumptionsByPeriod(@NotNull Supply supply,
                                                                 @NotNull OffsetDateTime startDate,
                                                                 @NotNull OffsetDateTime endDate);

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
