package org.lucoenergia.conluz.infrastructure.datadis;

import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Resolves the instant each hourly record of a supply, as returned by Datadis, is stored at. Datadis labels a record
 * with a local date and time and no offset, interpreted in the configured time zone.
 *
 * <p>Both hourly write paths, consumption and the production derived from it, take their instants from here, so a
 * record and the production derived from it are always stored at the same instant.</p>
 */
@Component
public class DatadisHourlyInstantResolver {

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm");

    private final TimeConfiguration timeConfiguration;

    public DatadisHourlyInstantResolver(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    /**
     * @param records the hourly records Datadis returned for one supply, in the order it returned them
     * @return each record with the instant it is stored at
     */
    public List<DatadisHourlyRecord<DatadisConsumption>> resolve(List<DatadisConsumption> records) {
        ZoneId zone = timeConfiguration.getZoneId();
        return records.stream()
                .map(record -> new DatadisHourlyRecord<>(
                        labelOf(record).atZone(zone).toInstant(), record))
                .toList();
    }

    private static LocalDateTime labelOf(DatadisConsumption record) {
        return LocalDateTime.parse(String.format("%sT%s", record.getDate(), record.getTime()), LABEL_FORMATTER);
    }
}
