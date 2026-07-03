package org.lucoenergia.conluz.domain.production.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Read port for Datadis-derived production time-series. Queries are scoped to a set of supply CUPS
 * (the plants owned by a community) rather than a single supply, because production is a communal
 * asset: see {@code GetDatadisProductionService} for the rationale.
 */
public interface GetDatadisProductionRepository {

    /**
     * Retrieves hourly production data within a date range, aggregated on the fly from the hourly
     * measurement and grouped per CUPS.
     *
     * @param cups the supply CUPS to include, must not be null or empty
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     */
    List<DatadisProduction> getHourlyProductionByRangeOfDates(@NotNull Collection<String> cups,
                                                              @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves daily production data within a date range, aggregated on the fly from the hourly
     * measurement and grouped per CUPS.
     *
     * @param cups the supply CUPS to include, must not be null or empty
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     */
    List<DatadisProduction> getDailyProductionByRangeOfDates(@NotNull Collection<String> cups,
                                                             @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves monthly production data within a date range from the pre-aggregated monthly measurement.
     *
     * @param cups the supply CUPS to include, must not be null or empty
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     */
    List<DatadisProduction> getMonthlyProductionByRangeOfDates(@NotNull Collection<String> cups,
                                                               @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves yearly production data within a date range from the pre-aggregated yearly measurement.
     *
     * @param cups the supply CUPS to include, must not be null or empty
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     */
    List<DatadisProduction> getYearlyProductionByRangeOfDates(@NotNull Collection<String> cups,
                                                              @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);
}
