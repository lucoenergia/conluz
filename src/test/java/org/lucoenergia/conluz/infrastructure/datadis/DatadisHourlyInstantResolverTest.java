package org.lucoenergia.conluz.infrastructure.datadis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Datadis labels each hourly record with a local date and the local time its hour ends, {@code 01:00} to
 * {@code 24:00}, with no offset. Every record carries a distinct consumption, so a record placed at another
 * record's instant, or two records collapsed onto one, is visible in the result.
 */
class DatadisHourlyInstantResolverTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String CUPS = "ES0000000000000000TT0F";

    private final DatadisHourlyInstantResolver resolver = new DatadisHourlyInstantResolver(timeConfiguration());

    private static TimeConfiguration timeConfiguration() {
        TimeConfiguration timeConfiguration = Mockito.mock(TimeConfiguration.class);
        when(timeConfiguration.getZoneId()).thenReturn(MADRID);
        return timeConfiguration;
    }

    @Test
    @DisplayName("DHI-001 AC1 an ordinary day's 01:00 record starts at local 00:00 and its 24:00 record at local 23:00")
    void anOrdinaryDayIsPlacedFromItsStart() {
        List<DatadisConsumption> records = new ArrayList<>();
        records.addAll(day("2025/11/03", ordinaryLabels(), 100f));
        records.addAll(day("2025/11/04", ordinaryLabels(), 200f));
        // Datadis returns a day in label order; the resolution must not depend on it.
        Collections.reverse(records);

        DatadisHourlyResolution resolution = resolver.resolve(records);

        assertTrue(resolution.mismatchedDays().isEmpty());
        Map<Float, Instant> instants = instantsByConsumption(resolution);
        assertEquals(48, instants.size());
        for (int hour = 1; hour <= 24; hour++) {
            assertEquals(local(2025, 11, 3, hour - 1), instants.get(100f + hour), "2025/11/03 " + hour + ":00");
            assertEquals(local(2025, 11, 4, hour - 1), instants.get(200f + hour), "2025/11/04 " + hour + ":00");
        }
        assertEquals(Instant.parse("2025-11-02T23:00:00Z"), instants.get(101f));
        assertEquals(Instant.parse("2025-11-03T22:00:00Z"), instants.get(124f));
    }

    @Test
    @DisplayName("DHI-001 AC2 the 25 records of the day the clocks go back get 25 distinct instants, the two 03:00 in the order Datadis returned them")
    void theDayTheClocksGoBackGetsTwentyFiveInstants() {
        // As Datadis returns 2025/10/26: 01:00, 02:00, 03:00, 03:00, 04:00, ..., 24:00.
        List<String> labels = new ArrayList<>(List.of("01:00", "02:00", "03:00", "03:00"));
        for (int hour = 4; hour <= 24; hour++) {
            labels.add(String.format("%02d:00", hour));
        }
        List<DatadisConsumption> transitionDay = day("2025/10/26", labels, 0f);
        List<DatadisConsumption> records = new ArrayList<>(day("2025/10/25", ordinaryLabels(), 100f));
        records.addAll(transitionDay);
        records.addAll(day("2025/10/27", ordinaryLabels(), 200f));

        DatadisHourlyResolution resolution = resolver.resolve(records);

        assertTrue(resolution.mismatchedDays().isEmpty());
        List<Instant> dayInstants = instantsOf(resolution, transitionDay);
        assertEquals(25, new HashSet<>(dayInstants).size());
        for (int position = 0; position < 25; position++) {
            assertEquals(Instant.parse("2025-10-25T22:00:00Z").plus(Duration.ofHours(position)),
                    dayInstants.get(position), "record " + (position + 1));
        }
        // The first 03:00 Datadis returned is the hour 02:00-03:00 CEST, the second the same local hour in CET.
        assertEquals(Instant.parse("2025-10-26T00:00:00Z"), instantsOf(resolution, transitionDay.subList(2, 3)).get(0));
        assertEquals(Instant.parse("2025-10-26T01:00:00Z"), instantsOf(resolution, transitionDay.subList(3, 4)).get(0));
        assertEquals(Instant.parse("2025-10-26T22:00:00Z"), dayInstants.get(24));
        // The neighbouring days are unaffected: they end where the transition day starts, and start where it ends.
        Map<Float, Instant> instants = instantsByConsumption(resolution);
        assertEquals(Instant.parse("2025-10-25T21:00:00Z"), instants.get(124f));
        assertEquals(Instant.parse("2025-10-26T23:00:00Z"), instants.get(201f));
    }

    @Test
    @DisplayName("DHI-001 AC3 the 23 records of the day the clocks go forward get 23 distinct instants, 04:00 at 01:00Z")
    void theDayTheClocksGoForwardGetsTwentyThreeInstants() {
        // As Datadis returns 2026/03/29: 01:00, 02:00, 04:00, ..., 24:00.
        List<String> labels = new ArrayList<>(List.of("01:00", "02:00"));
        for (int hour = 4; hour <= 24; hour++) {
            labels.add(String.format("%02d:00", hour));
        }
        List<DatadisConsumption> transitionDay = day("2026/03/29", labels, 0f);
        List<DatadisConsumption> records = new ArrayList<>(transitionDay);
        records.addAll(day("2026/03/30", ordinaryLabels(), 100f));

        DatadisHourlyResolution resolution = resolver.resolve(records);

        assertTrue(resolution.mismatchedDays().isEmpty());
        List<Instant> dayInstants = instantsOf(resolution, transitionDay);
        assertEquals(23, new HashSet<>(dayInstants).size());
        for (int position = 0; position < 23; position++) {
            assertEquals(Instant.parse("2026-03-28T23:00:00Z").plus(Duration.ofHours(position)),
                    dayInstants.get(position), "record " + (position + 1));
        }
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), instantsOf(resolution, transitionDay.subList(2, 3)).get(0));
        assertEquals(Instant.parse("2026-03-29T21:00:00Z"), dayInstants.get(22));
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), instantsByConsumption(resolution).get(101f));
    }

    @Test
    @DisplayName("DHI-002 AC4 a day missing 14:00 places each record one hour before its label and is reported once with its counts")
    void aDayMissingAnHourIsPlacedByLabel() {
        List<String> labels = new ArrayList<>(ordinaryLabels());
        labels.remove("14:00");
        List<DatadisConsumption> incompleteDay = day("2025/11/05", labels, 0f);
        List<DatadisConsumption> records = new ArrayList<>(day("2025/11/04", ordinaryLabels(), 100f));
        records.addAll(incompleteDay);

        DatadisHourlyResolution resolution = resolver.resolve(records);

        assertEquals(List.of(new DatadisHourlyResolution.MismatchedDay(LocalDate.of(2025, 11, 5), 23, 24)),
                resolution.mismatchedDays());
        Map<Float, Instant> instants = instantsByConsumption(resolution);
        // The 14th record is labelled 15:00, the hour from local 14:00: no later record shifts into the gap.
        assertEquals(local(2025, 11, 5, 14), instants.get(14f));
        assertEquals(local(2025, 11, 5, 12), instants.get(13f));
        assertFalse(instants.containsValue(local(2025, 11, 5, 13)), "nothing is stored for the missing hour");
        assertEquals(local(2025, 11, 5, 0), instants.get(1f));
        assertEquals(local(2025, 11, 5, 23), instants.get(23f), "24:00 is the midnight that ends the day");
        // The complete day before it is still placed by position.
        assertEquals(local(2025, 11, 4, 0), instants.get(101f));
        assertEquals(local(2025, 11, 4, 23), instants.get(124f));
    }

    @Test
    @DisplayName("DHI-002 a day with more records than hours is placed by label and reported with both counts")
    void aDayWithMoreRecordsThanHoursIsPlacedByLabel() {
        List<String> labels = new ArrayList<>(ordinaryLabels());
        labels.add(5, "05:00");
        List<DatadisConsumption> records = new ArrayList<>(day("2025/11/05", labels, 0f));
        records.addAll(day("2025/11/06", ordinaryLabels(), 100f));

        DatadisHourlyResolution resolution = resolver.resolve(records);

        assertEquals(List.of(new DatadisHourlyResolution.MismatchedDay(LocalDate.of(2025, 11, 5), 25, 24)),
                resolution.mismatchedDays());
        Map<Float, Instant> instants = instantsByConsumption(resolution);
        assertEquals(local(2025, 11, 5, 23), instants.get(25f));
        assertEquals(local(2025, 11, 6, 0), instants.get(101f));
    }

    private static List<String> ordinaryLabels() {
        List<String> labels = new ArrayList<>();
        for (int hour = 1; hour <= 24; hour++) {
            labels.add(String.format("%02d:00", hour));
        }
        return labels;
    }

    /**
     * One record per label, in the given order. The n-th record consumes {@code base + n}, so on a day whose labels
     * run {@code 01:00} to {@code 24:00} once each, the record labelled {@code HH:00} consumes {@code base + HH}.
     */
    private static List<DatadisConsumption> day(String date, List<String> labels, float base) {
        List<DatadisConsumption> records = new ArrayList<>();
        for (int position = 0; position < labels.size(); position++) {
            DatadisConsumption record = new DatadisConsumption();
            record.setCups(CUPS);
            record.setDate(date);
            record.setTime(labels.get(position));
            record.setConsumptionKWh(base + position + 1);
            record.setObtainMethod("Real");
            records.add(record);
        }
        return records;
    }

    private static Map<Float, Instant> instantsByConsumption(DatadisHourlyResolution resolution) {
        return resolution.records().stream()
                .collect(Collectors.toMap(hourly -> hourly.record().getConsumptionKWh(), DatadisHourlyRecord::instant));
    }

    /** The instants of the given records, in their order, found by identity. */
    private static List<Instant> instantsOf(DatadisHourlyResolution resolution, List<DatadisConsumption> records) {
        return records.stream()
                .map(record -> resolution.records().stream()
                        .filter(hourly -> hourly.record() == record)
                        .map(DatadisHourlyRecord::instant)
                        .reduce((first, second) -> {
                            throw new AssertionError("record resolved twice: " + record.getTime());
                        })
                        .orElseThrow(() -> new AssertionError("record not resolved: " + record.getTime())))
                .toList();
    }

    private static Instant local(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, MADRID).toInstant();
    }
}
