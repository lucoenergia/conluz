package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service for generating Datadis consumption reports scoped to a single community.
 */
public interface GetDatadisConsumptionReportService {

    /**
     * Retrieves hourly consumption data for the supplies of the given community within a date range
     * as a CSV output stream.
     *
     * @param startDate   the start date of the range, inclusive
     * @param endDate     the end date of the range, inclusive
     * @param communityId the community whose supplies are included in the report
     * @return an output stream with hourly consumption data for the community's supplies
     */
    ByteArrayOutputStream getHourlyConsumptionReportAsCsv(@NotNull OffsetDateTime startDate,
                                                          @NotNull OffsetDateTime endDate,
                                                          @NotNull UUID communityId);
}
