package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that daily consumption buckets follow the local calendar of the configured zone
 * ({@code conluz.time.zone.id}, Europe/Madrid under {@code application-test.properties}) rather than
 * UTC days.
 *
 * <p>Records are written with explicit UTC instants instead of going through
 * {@code PersistDatadisConsumptionRepository}: that path converts a local {@code date}/{@code time}
 * pair with {@code atZone}, which resolves the repeated hour of a fall-back day to a single offset,
 * so the two local 02:00 records would collapse onto the same instant and a 25-hour day could not be
 * expressed at all.
 *
 * <p>All energy values are multiples of 0.25 kWh, exactly representable in binary floating point, so
 * every expected sum below is asserted with a zero tolerance.
 */
class GetDatadisConsumptionRepositoryInfluxLocalCalendarTest extends BaseIntegrationTest {

    private static final String CUPS_A = "ES0031406912345678AA0F";
    private static final String CUPS_B = "ES0031406912345678BB0F";

    @Autowired
    @Qualifier("getDatadisConsumptionRepositoryInflux")
    private GetDatadisConsumptionRepository repository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private final Supply supplyA = SupplyMother.random().withCode(CUPS_A).build();
    private final Supply supplyB = SupplyMother.random().withCode(CUPS_B).build();

    @AfterEach
    void tearDown() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String cups : List.of(CUPS_A, CUPS_B)) {
                connection.query(new Query(String.format(
                        "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                        DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, cups)));
            }
        }
    }

    /**
     * AC1, winter (UTC+1). The record at 23:30 on the 15th belongs to the 15th and the one at 00:30
     * on the 16th to the 16th. The 00:30 record is the discriminating one: at UTC+1 it sits at
     * 23:30Z of the previous UTC day, so UTC bucketing counted it in the 15th.
     */
    @Test
    void winterRecordsAreCountedInTheirLocalDay() {
        write(CUPS_A, "2023-01-15T09:00:00Z", 1.0);   // local 2023-01-15 10:00
        write(CUPS_A, "2023-01-15T22:30:00Z", 0.25);  // local 2023-01-15 23:30
        write(CUPS_A, "2023-01-15T23:30:00Z", 0.5);   // local 2023-01-16 00:30
        write(CUPS_A, "2023-01-16T09:00:00Z", 2.0);   // local 2023-01-16 10:00
        write(CUPS_B, "2023-01-15T09:00:00Z", 8.0);   // another supply, must not leak into CUPS_A
        write(CUPS_B, "2023-01-15T23:30:00Z", 4.0);
        flush();

        Map<String, Float> daysOfA = consumptionByDate(supplyA,
                "2023-01-15T00:00:00+01:00", "2023-01-16T23:59:59+01:00");

        assertEquals(2, daysOfA.size());
        assertEquals(1.25f, daysOfA.get("2023/01/15"), 0.0f);
        assertEquals(2.5f, daysOfA.get("2023/01/16"), 0.0f);

        Map<String, Float> daysOfB = consumptionByDate(supplyB,
                "2023-01-15T00:00:00+01:00", "2023-01-16T23:59:59+01:00");

        assertEquals(8.0f, daysOfB.get("2023/01/15"), 0.0f);
        assertEquals(4.0f, daysOfB.get("2023/01/16"), 0.0f);
    }

    /**
     * AC1, summer (UTC+2). Same shape as winter, two hours further from UTC: the 00:30 record sits at
     * 22:30Z two calendar days earlier in UTC terms than the local day it belongs to.
     */
    @Test
    void summerRecordsAreCountedInTheirLocalDay() {
        write(CUPS_A, "2023-07-15T08:00:00Z", 1.0);   // local 2023-07-15 10:00
        write(CUPS_A, "2023-07-15T21:30:00Z", 0.25);  // local 2023-07-15 23:30
        write(CUPS_A, "2023-07-15T22:30:00Z", 0.5);   // local 2023-07-16 00:30
        write(CUPS_A, "2023-07-16T08:00:00Z", 2.0);   // local 2023-07-16 10:00
        flush();

        Map<String, Float> days = consumptionByDate(supplyA,
                "2023-07-15T00:00:00+02:00", "2023-07-16T23:59:59+02:00");

        assertEquals(2, days.size());
        assertEquals(1.25f, days.get("2023/07/15"), 0.0f);
        assertEquals(2.5f, days.get("2023/07/16"), 0.0f);
    }

    /**
     * AC2, spring forward. Europe/Madrid jumps from 02:00 CET to 03:00 CEST on 2023-03-26, so the
     * local day runs [2023-03-25T23:00Z, 2023-03-26T22:00Z) and holds 23 hourly records.
     */
    @Test
    void aSpringForwardDayCoversTwentyThreeHours() {
        int hours = writeHourlyRun(CUPS_A, "2023-03-25T23:00:00Z", 23, 0.25);
        assertEquals(23, hours);
        write(CUPS_A, "2023-03-25T22:00:00Z", 9.0);   // local 2023-03-25 23:00, the previous local day
        write(CUPS_A, "2023-03-26T22:00:00Z", 7.0);   // local 2023-03-27 00:00, the next local day
        flush();

        Map<String, Float> days = consumptionByDate(supplyA,
                "2023-03-25T00:00:00+01:00", "2023-03-27T23:59:59+02:00");

        assertEquals(3, days.size());
        assertTrue(days.containsKey("2023/03/26"), "Expected a bucket labelled with the transition day");
        assertEquals(5.75f, days.get("2023/03/26"), 0.0f, "23 hours of 0.25 kWh");
        assertEquals(9.0f, days.get("2023/03/25"), 0.0f);
        assertEquals(7.0f, days.get("2023/03/27"), 0.0f);
    }

    /**
     * AC2, fall back. Europe/Madrid repeats 02:00 on 2023-10-29, so the local day runs
     * [2023-10-28T22:00Z, 2023-10-29T23:00Z) and holds 25 hourly records.
     */
    @Test
    void aFallBackDayCoversTwentyFiveHours() {
        int hours = writeHourlyRun(CUPS_A, "2023-10-28T22:00:00Z", 25, 0.25);
        assertEquals(25, hours);
        write(CUPS_A, "2023-10-28T21:00:00Z", 9.0);   // local 2023-10-28 23:00, the previous local day
        write(CUPS_A, "2023-10-29T23:00:00Z", 7.0);   // local 2023-10-30 00:00, the next local day
        flush();

        Map<String, Float> days = consumptionByDate(supplyA,
                "2023-10-28T00:00:00+02:00", "2023-10-30T23:59:59+01:00");

        assertEquals(3, days.size());
        assertTrue(days.containsKey("2023/10/29"), "Expected a bucket labelled with the transition day");
        assertEquals(6.25f, days.get("2023/10/29"), 0.0f, "25 hours of 0.25 kWh");
        assertEquals(9.0f, days.get("2023/10/28"), 0.0f);
        assertEquals(7.0f, days.get("2023/10/30"), 0.0f);
    }

    /**
     * AC5. Both range bounds stay inclusive, so a record sitting exactly on either one is returned.
     */
    @Test
    void recordsOnTheInclusiveBoundsAreStillReturned() {
        write(CUPS_A, "2023-02-10T00:00:00Z", 1.0);   // exactly on startDate
        write(CUPS_A, "2023-02-11T12:00:00Z", 2.0);
        write(CUPS_A, "2023-02-12T23:59:59Z", 4.0);   // exactly on endDate
        write(CUPS_A, "2023-02-13T00:00:00Z", 8.0);   // one second past endDate
        flush();

        List<DatadisConsumption> result = repository.getDailyConsumptionsByRangeOfDates(supplyA,
                OffsetDateTime.parse("2023-02-10T00:00:00Z"), OffsetDateTime.parse("2023-02-12T23:59:59Z"));
        Map<String, Float> days = byDate(result);

        assertEquals(1.0f, days.get("2023/02/10"), 0.0f, "The record on startDate must be included");
        assertEquals(2.0f, days.get("2023/02/11"), 0.0f);
        assertEquals(4.0f, days.get("2023/02/13"), 0.0f,
                "23:59:59Z is 00:59:59 local on the 13th; the record on endDate must be included");
        assertFalse(days.containsValue(8.0f), "A record past endDate must not be returned");
    }

    /**
     * Bounds that are not local midnight produce partial buckets at both ends, so a caller wanting
     * whole local days passes them with the zone's offset. April 2023 requested that way is exactly
     * 30 buckets, the first labelled 2023/04/01 and the last 2023/04/30.
     */
    @Test
    void localOffsetBoundsYieldExactlyTheLocalMonth() {
        write(CUPS_A, "2023-03-31T21:00:00Z", 8.0);   // local 2023-03-31 23:00, before the month
        write(CUPS_A, "2023-03-31T22:00:00Z", 1.0);   // local 2023-04-01 00:00, the first local hour
        write(CUPS_A, "2023-04-15T10:00:00Z", 2.0);
        write(CUPS_A, "2023-04-30T21:00:00Z", 4.0);   // local 2023-04-30 23:00, the last local hour
        write(CUPS_A, "2023-04-30T22:00:00Z", 16.0);  // local 2023-05-01 00:00, after the month
        flush();

        List<DatadisConsumption> result = repository.getDailyConsumptionsByRangeOfDates(supplyA,
                OffsetDateTime.parse("2023-04-01T00:00:00+02:00"),
                OffsetDateTime.parse("2023-04-30T23:59:59+02:00"));

        assertEquals(30, result.size());
        assertEquals("2023/04/01", result.get(0).getDate());
        assertEquals("00:00", result.get(0).getTime());
        assertEquals("2023/04/30", result.get(29).getDate());

        Map<String, Float> days = byDate(result);
        assertEquals(1.0f, days.get("2023/04/01"), 0.0f);
        assertEquals(2.0f, days.get("2023/04/15"), 0.0f);
        assertEquals(4.0f, days.get("2023/04/30"), 0.0f);
    }

    // -----------------------------------------------------------------------
    // Data setup helpers
    // -----------------------------------------------------------------------

    private final List<Point> pending = new ArrayList<>();

    private void write(String cups, String utcInstant, double consumptionKWh) {
        pending.add(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT)
                .time(Instant.parse(utcInstant).toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("cups", cups)
                .addField("consumption_kwh", consumptionKWh)
                .addField("surplus_energy_kwh", 0.0)
                .addField("self_consumption_energy_kwh", 0.0)
                .addField("obtain_method", "Real")
                .build());
    }

    /**
     * Writes {@code count} consecutive hourly records starting at {@code firstUtcInstant}.
     *
     * @return how many records were written, so the caller can assert the run length it intended
     */
    private int writeHourlyRun(String cups, String firstUtcInstant, int count, double consumptionKWh) {
        Instant instant = Instant.parse(firstUtcInstant);
        for (int i = 0; i < count; i++) {
            write(cups, instant.plusSeconds(3600L * i).toString(), consumptionKWh);
        }
        return count;
    }

    private void flush() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            pending.forEach(batchPoints::point);
            connection.write(batchPoints);
        }
        pending.clear();
    }

    private Map<String, Float> consumptionByDate(Supply supply, String startDate, String endDate) {
        return byDate(repository.getDailyConsumptionsByRangeOfDates(supply,
                OffsetDateTime.parse(startDate), OffsetDateTime.parse(endDate)));
    }

    /**
     * Keeps only the buckets that actually carry energy: with the default {@code fill(null)} every
     * day of the range comes back, empty ones mapped to 0.0.
     */
    private Map<String, Float> byDate(List<DatadisConsumption> consumptions) {
        Map<String, Float> byDate = new LinkedHashMap<>();
        for (DatadisConsumption consumption : consumptions) {
            if (consumption.getConsumptionKWh() != null && consumption.getConsumptionKWh() != 0.0f) {
                byDate.put(consumption.getDate(), consumption.getConsumptionKWh());
            }
        }
        return byDate;
    }
}
