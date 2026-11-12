package org.lucoenergia.conluz.infrastructure.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxLoader;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Consumption data designed for the savings assertions, kept apart from
 * {@link DatadisConsumptionInfluxLoader} and under its own CUPS codes so neither fixture can
 * perturb the other's expectations.
 *
 * <p>Two properties the general fixture does not have:
 *
 * <ul>
 *   <li>every self-consumption figure is a multiple of {@code 0.25}, which is exact in binary, so an
 *       expected amount computed by hand is the amount the code must produce -- not an amount that
 *       happens to round the same way;</li>
 *   <li>the buckets cover the degenerate shapes on purpose: a day whose records carry no
 *       self-consumption, a day with no record at all, and two days whose amount lands exactly on a
 *       half cent so {@code HALF_UP} is actually exercised.</li>
 * </ul>
 *
 * <p>Timestamps are written as local Madrid times with their offset rather than as epoch constants:
 * what matters to these tests is which local day a record belongs to, and a constant would hide it.
 */
@Profile("test")
@Component
public class SupplyConsumptionSavingsInfluxLoader implements InfluxLoader {

    /** A supply with designed daily records and monthly pre-aggregates. */
    public static final String CUPS_WITH_SAVINGS = "ES0031406912345678JN0A";
    /** A second supply, so no assertion can pass by reading whatever the query happened to return. */
    public static final String OTHER_CUPS_WITH_SAVINGS = "ES0031406912345678JN0B";

    private static final String FIELD_CONSUMPTION_KWH = "consumption_kwh";
    private static final String FIELD_SURPLUS_ENERGY_KWH = "surplus_energy_kwh";
    private static final String FIELD_GENERATION_ENERGY_KWH = "generation_energy_kwh";
    private static final String FIELD_SELF_CONSUMPTION_ENERGY_KWH = "self_consumption_energy_kwh";
    private static final String FIELD_OBTAIN_METHOD = "obtain_method";
    private static final String TAG_CUPS = "cups";
    private static final String OBTAIN_METHOD = "Real";
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    /**
     * Hourly records, by local Madrid time. Daily totals of self-consumption, and the amount each
     * one is worth at the estimated 0.15 EUR/kWh:
     *
     * <ul>
     *   <li>{@code 2023/04/10} -- 0.25 + 0.75 + 1.00 = 2.00 kWh, worth 0.30. The 0.25 sits in the
     *       morning and the rest in the afternoon, so a request starting at midday sees 1.75 kWh,
     *       worth 0.2625, which presents as 0.26.</li>
     *   <li>{@code 2023/04/11} -- 0.25 + 0.25 = 0.50 kWh, worth 0.075: exactly a half cent, so it
     *       presents as 0.08 only under HALF_UP.</li>
     *   <li>{@code 2023/04/12} -- records but no self-consumption at all: worth 0.00.</li>
     *   <li>{@code 2023/04/13} -- no record at all; the day still comes back, at 0.00.</li>
     *   <li>{@code 2023/04/14} -- 1.25 + 0.25 = 1.50 kWh, worth 0.225: again a half cent, 0.23.</li>
     * </ul>
     *
     * <p>Every other hour of each day with a record is written as a published zero -- zero energy,
     * self-consumption present -- so the supply has data on those days and the totals above are
     * unchanged; 2023-04-13 stays without any record.
     *
     * <p>Generated energy is written too, and its daily totals are deliberately different from the
     * self-consumed ones -- 12.00, 2.00, 0.50, nothing and 4.00 kWh -- so an assertion on one field
     * cannot pass by reading the other.
     */
    private static final List<HourlyRecord> HOURLY_RECORDS = List.of(
            new HourlyRecord(CUPS_WITH_SAVINGS, "2023-04-10T09:00+02:00", 1.00d, 0.00d, 2.00d, 0.25d),
            new HourlyRecord(CUPS_WITH_SAVINGS, "2023-04-10T14:00+02:00", 2.00d, 0.50d, 4.00d, 0.75d),
            new HourlyRecord(CUPS_WITH_SAVINGS, "2023-04-10T18:00+02:00", 3.00d, 0.25d, 6.00d, 1.00d),
            new HourlyRecord(CUPS_WITH_SAVINGS, "2023-04-11T10:00+02:00", 1.00d, 0.00d, 1.00d, 0.25d),
            new HourlyRecord(CUPS_WITH_SAVINGS, "2023-04-11T11:00+02:00", 1.00d, 0.00d, 1.00d, 0.25d),
            new HourlyRecord(CUPS_WITH_SAVINGS, "2023-04-12T10:00+02:00", 1.00d, 0.00d, 0.50d, 0.00d),
            // 2023-04-13 is deliberately absent.
            new HourlyRecord(CUPS_WITH_SAVINGS, "2023-04-14T10:00+02:00", 2.00d, 0.00d, 3.00d, 1.25d),
            new HourlyRecord(CUPS_WITH_SAVINGS, "2023-04-14T11:00+02:00", 1.00d, 0.00d, 1.00d, 0.25d),

            // 4.00 kWh worth 0.60 on the 10th, 2.00 kWh worth 0.30 on the 11th.
            new HourlyRecord(OTHER_CUPS_WITH_SAVINGS, "2023-04-10T10:00+02:00", 5.00d, 0.00d, 8.00d, 4.00d),
            new HourlyRecord(OTHER_CUPS_WITH_SAVINGS, "2023-04-11T10:00+02:00", 3.00d, 0.00d, 4.00d, 2.00d));

    /**
     * Monthly totals. Each is written as the pre-aggregate the aggregation job would store, stamped
     * at local midnight of the first of its month, and as the hourly records it sums: every hour of
     * the month carrying self-consumption, zero at all of them except at 12:00 on the 10th and on the
     * 20th, which hold half of the month's totals each. Worth, at 0.15 EUR/kWh: 15.00, 0.08 (a half
     * cent again), 0.00 and 1.20 for the supply with savings; 3.00 for the other one. Up to the 15th,
     * February holds 0.25 kWh of self-consumption, worth 0.0375, which presents as 0.04.
     */
    private static final List<MonthlyRecord> MONTHLY_RECORDS = List.of(
            new MonthlyRecord(CUPS_WITH_SAVINGS, "2024-01-01T00:00+01:00", 400.00d, 20.00d, 500.00d, 100.00d),
            new MonthlyRecord(CUPS_WITH_SAVINGS, "2024-02-01T00:00+01:00", 380.00d, 15.00d, 480.00d, 0.50d),
            new MonthlyRecord(CUPS_WITH_SAVINGS, "2024-03-01T00:00+01:00", 350.00d, 10.00d, 450.00d, 0.00d),
            new MonthlyRecord(CUPS_WITH_SAVINGS, "2024-04-01T00:00+02:00", 300.00d, 25.00d, 400.00d, 8.00d),
            new MonthlyRecord(OTHER_CUPS_WITH_SAVINGS, "2024-01-01T00:00+01:00", 500.00d, 5.00d, 600.00d, 20.00d));

    private final InfluxDbConnectionManager influxDbConnectionManager;

    public SupplyConsumptionSavingsInfluxLoader(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void loadData() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            publishedZerosAroundHourlyRecords().forEach(batchPoints::point);
            HOURLY_RECORDS.forEach(record -> batchPoints.point(
                    pointAt(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, record.cups(), record.localTime(),
                            record.consumptionKWh(), record.surplusEnergyKWh(), record.generationEnergyKWh(),
                            record.selfConsumptionEnergyKWh())));
            MONTHLY_RECORDS.forEach(record -> batchPoints.point(
                    pointAt(DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT, record.cups(), record.localTime(),
                            record.consumptionKWh(), record.surplusEnergyKWh(), record.generationEnergyKWh(),
                            record.selfConsumptionEnergyKWh())));
            MONTHLY_RECORDS.forEach(record -> hourlyRecordsOf(record).forEach(batchPoints::point));

            connection.write(batchPoints);
        }
    }

    /**
     * A published zero at every hour of each local day holding one of the hourly records, except at
     * the hours those records occupy.
     */
    private static List<Point> publishedZerosAroundHourlyRecords() {
        Map<String, Set<Instant>> occupied = new HashMap<>();
        Map<String, Set<LocalDate>> days = new HashMap<>();
        for (HourlyRecord record : HOURLY_RECORDS) {
            ZonedDateTime time = OffsetDateTime.parse(record.localTime()).atZoneSameInstant(ZONE);
            occupied.computeIfAbsent(record.cups(), cups -> new HashSet<>()).add(time.toInstant());
            days.computeIfAbsent(record.cups(), cups -> new TreeSet<>()).add(time.toLocalDate());
        }
        List<Point> points = new ArrayList<>();
        days.forEach((cups, localDays) -> localDays.forEach(day -> {
            for (ZonedDateTime hour = day.atStartOfDay(ZONE); hour.toLocalDate().equals(day);
                 hour = hour.plusHours(1)) {
                if (!occupied.get(cups).contains(hour.toInstant())) {
                    points.add(pointAt(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, cups,
                            hour.toOffsetDateTime().toString(), 0d, 0d, 0d, 0d));
                }
            }
        }));
        return points;
    }

    /**
     * Every hour of the record's local month, zero except at 12:00 on the 10th and the 20th, which
     * hold half of each total.
     */
    private static List<Point> hourlyRecordsOf(MonthlyRecord record) {
        ZonedDateTime monthStart = OffsetDateTime.parse(record.localTime()).atZoneSameInstant(ZONE);
        ZonedDateTime monthEnd = monthStart.plusMonths(1);
        List<Point> points = new ArrayList<>();
        for (ZonedDateTime hour = monthStart; hour.isBefore(monthEnd); hour = hour.plusHours(1)) {
            boolean holdsHalf = hour.getHour() == 12 && (hour.getDayOfMonth() == 10 || hour.getDayOfMonth() == 20);
            double share = holdsHalf ? 0.5d : 0d;
            points.add(pointAt(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, record.cups(),
                    hour.toOffsetDateTime().toString(), record.consumptionKWh() * share,
                    record.surplusEnergyKWh() * share, record.generationEnergyKWh() * share,
                    record.selfConsumptionEnergyKWh() * share));
        }
        return points;
    }

    private static Point pointAt(String measurement, String cups, String localTime, double consumptionKWh,
                                 double surplusEnergyKWh, double generationEnergyKWh,
                                 double selfConsumptionEnergyKWh) {
        return Point.measurement(measurement)
                .time(OffsetDateTime.parse(localTime).toInstant().toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag(TAG_CUPS, cups)
                .addField(FIELD_CONSUMPTION_KWH, consumptionKWh)
                .addField(FIELD_SURPLUS_ENERGY_KWH, surplusEnergyKWh)
                .addField(FIELD_GENERATION_ENERGY_KWH, generationEnergyKWh)
                .addField(FIELD_SELF_CONSUMPTION_ENERGY_KWH, selfConsumptionEnergyKWh)
                .addField(FIELD_OBTAIN_METHOD, OBTAIN_METHOD)
                .build();
    }

    @Override
    public void clearData() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT)) {
                for (String cups : List.of(CUPS_WITH_SAVINGS, OTHER_CUPS_WITH_SAVINGS)) {
                    connection.query(new Query(String.format(
                            "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'", measurement, cups)));
                }
            }
        }
    }

    private record HourlyRecord(String cups, String localTime, double consumptionKWh,
                                double surplusEnergyKWh, double generationEnergyKWh,
                                double selfConsumptionEnergyKWh) {
    }

    private record MonthlyRecord(String cups, String localTime, double consumptionKWh,
                                 double surplusEnergyKWh, double generationEnergyKWh,
                                 double selfConsumptionEnergyKWh) {
    }
}
