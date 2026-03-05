package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;

/**
 * Service for generating Datadis consumption reports across all supplies.
 */
public interface GetDatadisConsumptionReportService {

    /**
     * Retrieves hourly consumption data for all supplies within a date range as a CSV output stream.
     *
     * @param startDate the start date of the range, inclusive
     * @param endDate the end date of the range, inclusive
     * @return an output stream with hourly consumption data for all supplies
     */
    ByteArrayOutputStream getHourlyConsumptionReportAsCsv(@NotNull OffsetDateTime startDate,
                                                          @NotNull OffsetDateTime endDate);
}
