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
     * @param supply the supply for which the consumption data is retrieved, must not be null
     * @param startDate the start date of the range, inclusive, must not be null
     * @param endDate the end date of the range, inclusive, must not be null
     * @return a list of {@code DatadisConsumption} objects representing daily consumption data within the specified date range
     */
    List<DatadisConsumption> getDailyConsumptionsByRangeOfDates(@NotNull Supply supply,
                                                                @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);
}
