package org.lucoenergia.conluz.infrastructure.datadis;

import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * The instants resolved for a supply's Datadis hourly records.
 *
 * @param records        every record with the instant it is stored at
 * @param mismatchedDays the days whose record count differs from their number of hours, placed by label
 */
public record DatadisHourlyResolution(List<DatadisHourlyRecord<DatadisConsumption>> records,
                                      List<MismatchedDay> mismatchedDays) {

    public DatadisHourlyResolution {
        records = List.copyOf(records);
        mismatchedDays = List.copyOf(mismatchedDays);
    }

    /**
     * A local calendar day whose records do not number its hours in the configured time zone.
     *
     * @param date          the day
     * @param foundRecords  how many records Datadis returned for it
     * @param expectedHours how many hours it has
     */
    public record MismatchedDay(LocalDate date, int foundRecords, int expectedHours) {
    }
}
