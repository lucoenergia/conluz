package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;
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
}
