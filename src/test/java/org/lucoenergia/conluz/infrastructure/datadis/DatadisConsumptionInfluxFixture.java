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
import java.util.stream.Stream;

/**
 * Writes and removes hourly consumption records for an arbitrary CUPS, for tests that need their
 * own dataset rather than the fixed one {@link DatadisConsumptionInfluxLoader} provides.
 *
 * <p>Records go through the production persistence repository, so a field left null on the
 * {@link DatadisConsumption} is absent from the stored point exactly as it would be in
 * production.</p>
 */
@Profile("test")
@Component
public class DatadisConsumptionInfluxFixture {

    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository;
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public DatadisConsumptionInfluxFixture(PersistDatadisConsumptionRepository persistDatadisConsumptionRepository,
                                           InfluxDbConnectionManager influxDbConnectionManager) {
        this.persistDatadisConsumptionRepository = persistDatadisConsumptionRepository;
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    /**
     * Builds an hourly record. A null energy value is stored as an absent field.
     *
     * @param date in {@code yyyy/MM/dd} format, interpreted in the application time zone
     * @param time in {@code HH:mm} format, interpreted in the application time zone
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

    public void write(List<DatadisConsumption> consumptions) {
        persistDatadisConsumptionRepository.persistHourlyConsumptions(consumptions);
    }

    /**
     * Writes one hourly record at an exact instant, with the same fields the production persistence
     * writes; a null energy value is stored as an absent field.
     *
     * <p>The production path addresses records by local date and time, so it cannot write both
     * records of the local hour a daylight saving fall-back repeats. This one can.</p>
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
