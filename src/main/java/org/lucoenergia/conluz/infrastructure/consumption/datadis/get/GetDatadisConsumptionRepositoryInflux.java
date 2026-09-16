package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionMonthlyPoint;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionYearlyPoint;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDuration;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Month;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@Qualifier("getDatadisConsumptionRepositoryInflux")
public class GetDatadisConsumptionRepositoryInflux implements GetDatadisConsumptionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;
    private final ZoneResolver zoneResolver;

    public GetDatadisConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                 DateConverter dateConverter,
                                                 ZoneResolver zoneResolver) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionsByMonth(Supply supply, Month month, int year) {

        String startDate = dateConverter.convertToFirstDayOfTheMonthAsString(month, year);
        String endDate = dateConverter.convertToLastDayOfTheMonthAsString(month, year);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    startDate,
                    endDate));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, DatadisConsumptionPoint.class);
            return mapToConsumption(consumptionPoints);
        }
    }

    @Override
    public List<DatadisConsumption> getDailyConsumptionsByRangeOfDates(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate) {
        return getConsumptionsByRangeOfDatesGroupedByDuration(supply, startDate, endDate,
                DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, InfluxDuration.DAILY, true);
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionsByRangeOfDates(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate) {
        return getConsumptionsByRangeOfDatesGroupedByDuration(supply, startDate, endDate,
                DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, InfluxDuration.HOURLY, false);
    }

    @Override
    public List<DatadisConsumption> getMonthlyConsumptionsByRangeOfDates(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT,
                    supply.getCode(),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisConsumptionMonthlyPoint> consumptionPoints = resultMapper.toPOJO(queryResult, DatadisConsumptionMonthlyPoint.class);
            return mapMonthlyToConsumption(consumptionPoints);
        }
    }

    @Override
    public List<DatadisConsumption> getYearlyConsumptionsByRangeOfDates(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT,
                    supply.getCode(),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisConsumptionYearlyPoint> consumptionPoints = resultMapper.toPOJO(queryResult, DatadisConsumptionYearlyPoint.class);
            return mapYearlyToConsumption(consumptionPoints);
        }
    }

    /**
     * Grouped sums over an interval that is <strong>inclusive on both ends</strong>, exactly as the
     * public consumption endpoints document it: the bounds are the caller's instants, unadjusted.
     *
     * <p>{@code localCalendarAligned} appends {@code tz('<zone>')} -- the zone
     * {@link ZoneResolver#resolveZoneIdForSupply(java.util.UUID)} gives for this supply -- so that
     * {@code GROUP BY time(1d)} buckets start at local midnight instead of UTC midnight, which also
     * makes a spring-forward day 23 hours long and a fall-back day 25. The clause comes last in the
     * statement, as in {@code GetProductionRepositoryInflux}, where the same InfluxQL construct was
     * verified against a live InfluxDB 1.8.
     *
     * <p>Only the daily grouping passes {@code true}. Hourly buckets are whole-hour aligned and every
     * zone offset in use is a whole number of hours, so alignment would be a no-op there; leaving the
     * hourly query byte-identical also keeps it clear of InfluxDB 1.x's {@code tz()} behaviour across
     * a DST fall-back, which this change does not examine.
     *
     * <p>No {@code fill()} is emitted, so InfluxDB's default {@code fill(null)} stands and a bucket
     * with no record is still returned (mapped to {@code 0.0} downstream) rather than omitted.
     */
    private List<DatadisConsumption> getConsumptionsByRangeOfDatesGroupedByDuration(Supply supply, OffsetDateTime startDate,
                                                                         OffsetDateTime endDate, String measurementName,
                                                                         String duration, boolean localCalendarAligned) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            String timeZoneClause = localCalendarAligned
                    ? String.format(" tz('%s')", zoneResolver.resolveZoneIdForSupply(supply.getId()).getId())
                    : "";

            Query query = new Query(String.format(
                    """
                            SELECT
                                SUM("consumption_kwh") AS "consumption_kwh",
                                SUM("surplus_energy_kwh") AS "surplus_energy_kwh",
                                SUM("self_consumption_energy_kwh") AS "self_consumption_energy_kwh",
                                LAST("obtain_method") AS "obtain_method"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time <= '%s'
                            GROUP BY time(%s), cups%s
                            """,
                    measurementName,
                    supply.getCode(),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate),
                    duration,
                    timeZoneClause));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, DatadisConsumptionPoint.class);
            return mapToConsumption(consumptionPoints);
        }
    }

    private List<DatadisConsumption> mapToConsumption(List<DatadisConsumptionPoint> consumptionPoints) {
        // Map fields from datadisConsumptionPoint to consumption here
        return consumptionPoints.stream()
                .map(consumptionPoint -> {
                    DatadisConsumption consumption = new DatadisConsumption();
                    consumption.setCups(consumptionPoint.getCups());
                    consumption.setDate(dateConverter.convertFromInstantToStringDate(consumptionPoint.getTime()));
                    consumption.setTime(dateConverter.convertFromInstantToStringTime(consumptionPoint.getTime()));
                    consumption.setConsumptionKWh(parseToFloat(consumptionPoint.getConsumptionKWh()));
                    consumption.setSelfConsumptionEnergyKWh(parseToFloat(consumptionPoint.getSelfConsumptionEnergyKWh()));
                    consumption.setSurplusEnergyKWh(parseToFloat(consumptionPoint.getSurplusEnergyKWh()));
                    consumption.setGenerationEnergyKWh(parseToFloat(consumptionPoint.getGenerationEnergyKWh()));
                    consumption.setObtainMethod(consumptionPoint.getObtainMethod());
                    return consumption;
                })
                .toList();
    }

    private List<DatadisConsumption> mapMonthlyToConsumption(List<DatadisConsumptionMonthlyPoint> consumptionPoints) {
        return consumptionPoints.stream()
                .map(consumptionPoint -> {
                    DatadisConsumption consumption = new DatadisConsumption();
                    consumption.setCups(consumptionPoint.getCups());
                    consumption.setDate(dateConverter.convertFromInstantToStringDate(consumptionPoint.getTime()));
                    consumption.setTime(dateConverter.convertFromInstantToStringTime(consumptionPoint.getTime()));
                    consumption.setConsumptionKWh(parseToFloat(consumptionPoint.getConsumptionKWh()));
                    consumption.setSelfConsumptionEnergyKWh(parseToFloat(consumptionPoint.getSelfConsumptionEnergyKWh()));
                    consumption.setSurplusEnergyKWh(parseToFloat(consumptionPoint.getSurplusEnergyKWh()));
                    consumption.setGenerationEnergyKWh(parseToFloat(consumptionPoint.getGenerationEnergyKWh()));
                    consumption.setObtainMethod(consumptionPoint.getObtainMethod());
                    return consumption;
                })
                .toList();
    }

    private List<DatadisConsumption> mapYearlyToConsumption(List<DatadisConsumptionYearlyPoint> consumptionPoints) {
        return consumptionPoints.stream()
                .map(consumptionPoint -> {
                    DatadisConsumption consumption = new DatadisConsumption();
                    consumption.setCups(consumptionPoint.getCups());
                    consumption.setDate(dateConverter.convertFromInstantToStringDate(consumptionPoint.getTime()));
                    consumption.setTime(dateConverter.convertFromInstantToStringTime(consumptionPoint.getTime()));
                    consumption.setConsumptionKWh(parseToFloat(consumptionPoint.getConsumptionKWh()));
                    consumption.setSelfConsumptionEnergyKWh(parseToFloat(consumptionPoint.getSelfConsumptionEnergyKWh()));
                    consumption.setSurplusEnergyKWh(parseToFloat(consumptionPoint.getSurplusEnergyKWh()));
                    consumption.setGenerationEnergyKWh(parseToFloat(consumptionPoint.getGenerationEnergyKWh()));
                    consumption.setObtainMethod(consumptionPoint.getObtainMethod());
                    return consumption;
                })
                .toList();
    }

    private Float parseToFloat(Double value) {
        if (value == null) {
            return 0.0f;
        }
        try {
            return value.floatValue();
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }
}