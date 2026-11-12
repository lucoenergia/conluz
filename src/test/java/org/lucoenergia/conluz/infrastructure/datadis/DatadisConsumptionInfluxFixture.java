package org.lucoenergia.conluz.infrastructure.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes and removes hourly consumption records for an arbitrary CUPS, for tests that need their
 * own dataset rather than the fixed one {@link DatadisConsumptionInfluxLoader} provides.
 *
 * <p>Records go through the production instant resolution and persistence repository, so a field left null on the
 * {@link DatadisConsumption} is absent from the stored point exactly as it would be in
 * production.</p>
 */
@Profile("test")
@Component
public class DatadisConsumptionInfluxFixture {

    /** The local time a record's hour ends at, as Datadis labels it: {@code 01:00} to {@code 24:00}. */
    private static final Pattern DATADIS_LABEL = Pattern.compile("(0[1-9]|1[0-9]|2[0-4]):00");

    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository;
    private final DatadisHourlyInstantResolver datadisHourlyInstantResolver;
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public DatadisConsumptionInfluxFixture(PersistDatadisConsumptionRepository persistDatadisConsumptionRepository,
                                           DatadisHourlyInstantResolver datadisHourlyInstantResolver,
                                           InfluxDbConnectionManager influxDbConnectionManager) {
        this.persistDatadisConsumptionRepository = persistDatadisConsumptionRepository;
        this.datadisHourlyInstantResolver = datadisHourlyInstantResolver;
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    /**
     * Builds an hourly record. A null energy value is stored as an absent field.
     *
     * @param date in {@code yyyy/MM/dd} format, interpreted in the application time zone
     * @param time the Datadis label in {@code HH:mm} format: the local time the record's hour ends at,
     *             {@code 01:00} to {@code 24:00}
     */
    public static DatadisConsumption hourlyRecord(String cups, String date, String time, Float consumptionKWh,
                                                  Float selfConsumptionEnergyKWh, Float surplusEnergyKWh) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups(cups);
        consumption.setDate(date);
        consumption.setTime(time);
        consumption.setConsumptionKWh(consumptionKWh);
        consumption.setSelfConsumptionEnergyKWh(selfConsumptionEnergyKWh);
        consumption.setSurplusEnergyKWh(surplusEnergyKWh);
        consumption.setObtainMethod("Real");
        return consumption;
    }

    /**
     * Stores the records at the instants the Datadis sync would resolve for them, as one batch of the same
     * supply: a day is placed by position only when the batch holds all of its hours, otherwise each record
     * goes one hour before its label.
     *
     * @throws IllegalArgumentException if a record's {@code time} is not a Datadis label, {@code 01:00} to
     *                                  {@code 24:00}
     */
    public void write(List<DatadisConsumption> consumptions) {
        for (DatadisConsumption consumption : consumptions) {
            if (consumption.getTime() == null || !DATADIS_LABEL.matcher(consumption.getTime()).matches()) {
                throw new IllegalArgumentException(String.format(
                        "Record on %s has time '%s', which is not a Datadis label: Datadis labels the end of the hour, "
                                + "from 01:00 to 24:00", consumption.getDate(), consumption.getTime()));
            }
        }
        persistDatadisConsumptionRepository.persistHourlyConsumptions(
                datadisHourlyInstantResolver.resolve(consumptions).records());
    }

    /**
     * Writes one hourly record at an exact instant, with the same fields the production persistence
     * writes; a null energy value is stored as an absent field.
     *
     * <p>The production path places the two records of the local hour a daylight saving fall-back repeats
     * only when it receives the whole day. This one writes either of them on its own.</p>
     */
    public void writeAt(String cups, Instant time, Float consumptionKWh, Float selfConsumptionEnergyKWh,
                        Float surplusEnergyKWh) {
        writeAt(cups, List.of(time), consumptionKWh, selfConsumptionEnergyKWh, surplusEnergyKWh);
    }

    /**
     * As {@link #writeAt(String, Instant, Float, Float, Float)}, one record per instant, all with the
     * same values, in a single batch.
     */
    public void writeAt(String cups, List<Instant> times, Float consumptionKWh, Float selfConsumptionEnergyKWh,
                        Float surplusEnergyKWh) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            for (Instant time : times) {
                batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT)
                        .time(time.toEpochMilli(), TimeUnit.MILLISECONDS)
                        .tag("cups", cups)
                        .addField("consumption_kwh", consumptionKWh)
                        .addField("obtain_method", "Real")
                        .addField("surplus_energy_kwh", surplusEnergyKWh)
                        .addField("self_consumption_energy_kwh", selfConsumptionEnergyKWh)
                        .build());
            }
            connection.write(batchPoints);
        }
    }

    /**
     * Writes a published month that adds nothing to any sum: a record at every hour of the month in
     * the zone carrying zero consumption, self-consumption and surplus, except at the given local
     * hours of the day, which are left without any record.
     *
     * <p>A month counts as published only once most of its hours carry self-consumption. A test whose
     * own records are sparse writes this first and its own records afterwards: a record written later
     * at the same instant replaces the fields it carries.</p>
     */
    public void writePublishedZeros(String cups, YearMonth month, ZoneId zone, Integer... skippedLocalHours) {
        Set<Integer> skipped = Set.of(skippedLocalHours);
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        List<Instant> times = Stream.iterate(month.atDay(1).atStartOfDay(zone).toInstant(),
                        time -> time.isBefore(end), time -> time.plus(Duration.ofHours(1)))
                .filter(time -> !skipped.contains(time.atZone(zone).getHour()))
                .toList();
        writeAt(cups, times, 0f, 0f, 0f);
    }

    /**
     * Writes a monthly pre-aggregate point for the CUPS directly, stamped at local midnight of the
     * month's first day as the production aggregation stamps it, without summing any hourly record:
     * a month aggregated from data that has changed since.
     */
    public void writeMonthlyAggregate(String cups, YearMonth month, ZoneId zone, double consumptionKWh,
                                      double selfConsumptionEnergyKWh, double surplusEnergyKWh) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT)
                    .time(month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(), TimeUnit.MILLISECONDS)
                    .tag("cups", cups)
                    .addField("consumption_kwh", consumptionKWh)
                    .addField("self_consumption_energy_kwh", selfConsumptionEnergyKWh)
                    .addField("surplus_energy_kwh", surplusEnergyKWh)
                    .addField("generation_energy_kwh", 0.0)
                    .addField("obtain_method", "Real")
                    .build());
            connection.write(batchPoints);
        }
    }

    /**
     * Removes the monthly pre-aggregates of the CUPS, for tests that build them through the
     * production aggregation from the hourly records they write.
     */
    public void clearMonthlyAggregates(String cups) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT, cups)));
        }
    }

    public void clear(String cups) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, cups)));
        }
    }
}
