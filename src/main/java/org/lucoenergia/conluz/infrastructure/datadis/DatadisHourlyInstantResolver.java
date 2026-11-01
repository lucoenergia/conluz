package org.lucoenergia.conluz.infrastructure.datadis;

import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the instant each hourly record of a supply, as returned by Datadis, is stored at: the start of the hour
 * the record measures.
 *
 * <p>Datadis labels a record with a local date and the local time its hour <em>ends</em> ({@code 01:00} to
 * {@code 24:00}), with no offset. On the day the clocks go back it repeats {@code 03:00}; on the day they go forward
 * it omits it. So a day whose records number exactly its hours in the configured time zone (23, 24 or 25) is placed
 * by position: the n-th record, ordered by its label with ties kept in the order Datadis returned them, starts
 * (n - 1) hours after the day's start. Any other day is placed by label, one hour before the local instant the label
 * names, and is reported back so the caller can warn about it.</p>
 *
 * <p>Both hourly write paths, consumption and the production derived from it, take their instants from here, so a
 * record and the production derived from it are always stored at the same instant.</p>
 */
@Component
public class DatadisHourlyInstantResolver {

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm");
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final TimeConfiguration timeConfiguration;

    public DatadisHourlyInstantResolver(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    /**
     * @param records the hourly records Datadis returned for one supply, in the order it returned them
     * @return each record with the instant it is stored at, and the days placed by label
     */
    public DatadisHourlyResolution resolve(List<DatadisConsumption> records) {
        ZoneId zone = timeConfiguration.getZoneId();

        Map<String, List<DatadisConsumption>> recordsByDate = new LinkedHashMap<>();
        for (DatadisConsumption record : records) {
            recordsByDate.computeIfAbsent(record.getDate(), date -> new ArrayList<>()).add(record);
        }

        List<DatadisHourlyRecord<DatadisConsumption>> resolved = new ArrayList<>(records.size());
        List<DatadisHourlyResolution.MismatchedDay> mismatchedDays = new ArrayList<>();
        recordsByDate.forEach((date, dayRecords) ->
                resolveDay(DateConverter.convertStringToLocalDate(date), dayRecords, zone, resolved, mismatchedDays));

        return new DatadisHourlyResolution(resolved, mismatchedDays);
    }

    private static void resolveDay(LocalDate date, List<DatadisConsumption> dayRecords, ZoneId zone,
                                   List<DatadisHourlyRecord<DatadisConsumption>> resolved,
                                   List<DatadisHourlyResolution.MismatchedDay> mismatchedDays) {
        // A stable sort, so records sharing a label keep the order Datadis returned them in.
        List<LabelledRecord> ordered = dayRecords.stream()
                .map(record -> new LabelledRecord(labelOf(record), record))
                .sorted(Comparator.comparing(LabelledRecord::label))
                .toList();

        Instant dayStart = date.atStartOfDay(zone).toInstant();
        int expectedHours = (int) Duration.between(dayStart, date.plusDays(1).atStartOfDay(zone).toInstant()).toHours();

        if (ordered.size() == expectedHours) {
            for (int position = 0; position < ordered.size(); position++) {
                resolved.add(new DatadisHourlyRecord<>(dayStart.plus(ONE_HOUR.multipliedBy(position)),
                        ordered.get(position).record()));
            }
            return;
        }

        mismatchedDays.add(new DatadisHourlyResolution.MismatchedDay(date, ordered.size(), expectedHours));
        for (LabelledRecord labelled : ordered) {
            resolved.add(new DatadisHourlyRecord<>(labelled.label().atZone(zone).toInstant().minus(ONE_HOUR),
                    labelled.record()));
        }
    }

    /**
     * The local date and time the record's label names. {@code 24:00} is the midnight that ends the day.
     */
    private static LocalDateTime labelOf(DatadisConsumption record) {
        return LocalDateTime.parse(String.format("%sT%s", record.getDate(), record.getTime()), LABEL_FORMATTER);
    }

    private record LabelledRecord(LocalDateTime label, DatadisConsumption record) {
    }
}
