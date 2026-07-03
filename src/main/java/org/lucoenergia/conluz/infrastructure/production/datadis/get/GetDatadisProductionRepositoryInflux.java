package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionRepository;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMeasurements;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMonthlyPoint;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionPoint;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionYearlyPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDuration;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * InfluxDB read adapter for Datadis production. Mirrors
 * {@code GetDatadisConsumptionRepositoryInflux}: hourly and daily are aggregated on the fly from the
 * hourly measurement with {@code GROUP BY time(<duration>), cups}, while monthly and yearly read the
 * pre-aggregated measurements directly. Carries no {@code @Transactional} (InfluxDB is not
 * transactional; the read boundary lives on the service).
 */
@Repository
@Qualifier("getDatadisProductionRepositoryInflux")
public class GetDatadisProductionRepositoryInflux implements GetDatadisProductionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetDatadisProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<DatadisProduction> getHourlyProductionByRangeOfDates(Collection<String> cups,
                                                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        return getProductionByRangeOfDatesGroupedByDuration(cups, startDate, endDate,
                DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT, InfluxDuration.HOURLY);
    }

    @Override
    public List<DatadisProduction> getDailyProductionByRangeOfDates(Collection<String> cups,
                                                                    OffsetDateTime startDate, OffsetDateTime endDate) {
        return getProductionByRangeOfDatesGroupedByDuration(cups, startDate, endDate,
                DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT, InfluxDuration.DAILY);
    }

    @Override
    public List<DatadisProduction> getMonthlyProductionByRangeOfDates(Collection<String> cups,
                                                                      OffsetDateTime startDate, OffsetDateTime endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE (%s) AND time >= '%s' AND time <= '%s'",
                    DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT,
                    buildCupsPredicate(cups),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisProductionMonthlyPoint> points = resultMapper.toPOJO(queryResult, DatadisProductionMonthlyPoint.class);
            return mapMonthlyToProduction(points);
        }
    }

    @Override
    public List<DatadisProduction> getYearlyProductionByRangeOfDates(Collection<String> cups,
                                                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE (%s) AND time >= '%s' AND time <= '%s'",
                    DatadisProductionMeasurements.PRODUCTION_KWH_YEAR_MEASUREMENT,
                    buildCupsPredicate(cups),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisProductionYearlyPoint> points = resultMapper.toPOJO(queryResult, DatadisProductionYearlyPoint.class);
            return mapYearlyToProduction(points);
        }
    }

    private List<DatadisProduction> getProductionByRangeOfDatesGroupedByDuration(Collection<String> cups,
                                                                                 OffsetDateTime startDate, OffsetDateTime endDate,
                                                                                 String measurementName, String duration) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Bounds mirror the consumption read side verbatim: no explicit day snapping and no tz()
            // clause, so GROUP BY time(1d) buckets align to UTC midnight (see GetDatadisConsumptionRepositoryInflux).
            Instant queryStart = dateConverter.toLocalDayInstant(startDate);
            Instant queryEnd = dateConverter.toLocalDayInstant(endDate);

            Query query = new Query(String.format(
                    """
                            SELECT
                                SUM("production_kwh") AS "production_kwh",
                                LAST("obtain_method") AS "obtain_method"
                            FROM "%s"
                            WHERE (%s)
                                AND time >= '%s'
                                AND time <= '%s'
                            GROUP BY time(%s), cups
                            """,
                    measurementName,
                    buildCupsPredicate(cups),
                    dateConverter.convertToString(queryStart),
                    dateConverter.convertToString(queryEnd),
                    duration));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisProductionPoint> points = resultMapper.toPOJO(queryResult, DatadisProductionPoint.class);
            return mapToProduction(points);
        }
    }

    private String buildCupsPredicate(Collection<String> cups) {
        return cups.stream()
                .map(code -> String.format("cups = '%s'", code))
                .collect(Collectors.joining(" OR "));
    }

    private List<DatadisProduction> mapToProduction(List<DatadisProductionPoint> points) {
        return points.stream()
                .map(point -> {
                    DatadisProduction production = new DatadisProduction();
                    production.setCups(point.getCups());
                    production.setDate(dateConverter.convertFromInstantToStringDate(point.getTime()));
                    production.setTime(dateConverter.convertFromInstantToStringTime(point.getTime()));
                    production.setProductionKWh(parseToFloat(point.getProductionKWh()));
                    production.setObtainMethod(point.getObtainMethod());
                    return production;
                })
                .toList();
    }

    private List<DatadisProduction> mapMonthlyToProduction(List<DatadisProductionMonthlyPoint> points) {
        return points.stream()
                .map(point -> {
                    DatadisProduction production = new DatadisProduction();
                    production.setCups(point.getCups());
                    production.setDate(dateConverter.convertFromInstantToStringDate(point.getTime()));
                    production.setTime(dateConverter.convertFromInstantToStringTime(point.getTime()));
                    production.setProductionKWh(parseToFloat(point.getProductionKWh()));
                    production.setObtainMethod(point.getObtainMethod());
                    return production;
                })
                .toList();
    }

    private List<DatadisProduction> mapYearlyToProduction(List<DatadisProductionYearlyPoint> points) {
        return points.stream()
                .map(point -> {
                    DatadisProduction production = new DatadisProduction();
                    production.setCups(point.getCups());
                    production.setDate(dateConverter.convertFromInstantToStringDate(point.getTime()));
                    production.setTime(dateConverter.convertFromInstantToStringTime(point.getTime()));
                    production.setProductionKWh(parseToFloat(point.getProductionKWh()));
                    production.setObtainMethod(point.getObtainMethod());
                    return production;
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
