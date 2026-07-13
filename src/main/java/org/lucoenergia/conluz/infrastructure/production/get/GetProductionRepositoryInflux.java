package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.production.HuaweiHourlyProductionMonthlyPoint;
import org.lucoenergia.conluz.infrastructure.production.HuaweiHourlyProductionYearlyPoint;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDuration;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Repository
public class GetProductionRepositoryInflux implements GetProductionRepository {

    private static final String STATION_CODE_TAG = "station_code";

    private final InfluxDbConnectionManager influxDbConnectionManager;

    private final DateConverter dateConverter;

    private final InstantProductionInfluxMapper instantProductionInfluxMapper;
    private final ProductionByHourInfluxMapper productionByHourInfluxMapper;

    public GetProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                         DateConverter dateConverter,
                                         InstantProductionInfluxMapper instantProductionInfluxMapper,
                                         ProductionByHourInfluxMapper productionByHourInfluxMapper) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
        this.instantProductionInfluxMapper = instantProductionInfluxMapper;
        this.productionByHourInfluxMapper = productionByHourInfluxMapper;
    }

    @Override
    public InstantProduction getInstantProduction(Collection<String> stationCodes) {
        if (stationCodes == null || stationCodes.isEmpty()) {
            return new InstantProduction(0.0d);
        }
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT LAST(\"%s\") AS \"%s\" FROM \"%s\" WHERE %s",
                    ProductionPoint.INVERTER_POWER,
                    ProductionPoint.INVERTER_POWER,
                    HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                    stationCodeOrChain(stationCodes)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, ProductionPoint.class);

            return measurementPoints.stream().findFirst()
                    .map(instantProductionInfluxMapper::map)
                    .orElseGet(() -> new InstantProduction(0.0d));
        }
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                    Float partitionCoefficient,
                                                                    Collection<String> stationCodes) {
        if (stationCodes == null || stationCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return queryHourly(startDate, endDate, partitionCoefficient, stationCodeAndClause(stationCodes));
    }

    private List<ProductionByTime> queryHourly(OffsetDateTime startDate, OffsetDateTime endDate,
                                               Float partitionCoefficient, String stationClause) {
        // Unreachable through the public API today — every public caller already guards on an empty
        // stationCodes collection before building a non-blank clause. Kept as the actual invariant point
        // so no future caller (public or private) can ever emit an unrestricted query.
        if (stationClause == null || stationClause.isBlank()) {
            return Collections.emptyList();
        }
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT time, \"%s\"*%s FROM \"%s\" WHERE time >= '%s' AND time <= '%s'%s",
                    ProductionPoint.INVERTER_POWER,
                    partitionCoefficient,
                    HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate),
                    stationClause));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, ProductionPoint.class);

            return productionByHourInfluxMapper.mapList(measurementPoints);
        }
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                   Float partitionCoefficient,
                                                                   Collection<String> stationCodes) {
        if (stationCodes == null || stationCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate,
                partitionCoefficient, InfluxDuration.DAILY, stationCodeAndClause(stationCodes));
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                     Float partitionCoefficient,
                                                                     Collection<String> stationCodes) {
        if (stationCodes == null || stationCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return queryAggregatedMeasurement(HuaweiConfig.HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT,
                startDate, endDate, partitionCoefficient, stationCodeAndClause(stationCodes),
                HuaweiHourlyProductionMonthlyPoint.class);
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                    Float partitionCoefficient,
                                                                    Collection<String> stationCodes) {
        if (stationCodes == null || stationCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return queryAggregatedMeasurement(HuaweiConfig.HUAWEI_YEARLY_PRODUCTION_MEASUREMENT,
                startDate, endDate, partitionCoefficient, stationCodeAndClause(stationCodes),
                HuaweiHourlyProductionYearlyPoint.class);
    }

    private <T> List<ProductionByTime> queryAggregatedMeasurement(String measurement, OffsetDateTime startDate,
                                                                  OffsetDateTime endDate, Float partitionCoefficient,
                                                                  String stationClause, Class<T> pointType) {
        // Unreachable through the public API today — every public caller already guards on an empty
        // stationCodes collection before building a non-blank clause. Kept as the actual invariant point
        // so no future caller (public or private) can ever emit an unrestricted query.
        if (stationClause == null || stationClause.isBlank()) {
            return Collections.emptyList();
        }
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT \"inverter_power\" FROM \"%s\" WHERE time >= '%s' AND time <= '%s'%s",
                    measurement,
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate),
                    stationClause));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<T> points = resultMapper.toPOJO(queryResult, pointType);

            return groupByTimeAndApplyCoefficient(points, partitionCoefficient);
        }
    }

    private <T> List<ProductionByTime> groupByTimeAndApplyCoefficient(List<T> points, Float partitionCoefficient) {
        TreeMap<Instant, Double> grouped = new TreeMap<>();
        for (T point : points) {
            Instant time;
            Double inverterPower;
            if (point instanceof HuaweiHourlyProductionMonthlyPoint monthlyPoint) {
                time = monthlyPoint.getTime();
                inverterPower = monthlyPoint.getInverterPower();
            } else if (point instanceof HuaweiHourlyProductionYearlyPoint yearlyPoint) {
                time = yearlyPoint.getTime();
                inverterPower = yearlyPoint.getInverterPower();
            } else {
                continue;
            }
            grouped.merge(time, inverterPower != null ? inverterPower : 0.0, Double::sum);
        }

        List<ProductionByTime> result = new ArrayList<>();
        for (Map.Entry<Instant, Double> entry : grouped.entrySet()) {
            result.add(new ProductionByTime(
                    dateConverter.convertInstantToOffsetDateTime(entry.getKey()),
                    entry.getValue() * partitionCoefficient));
        }
        return result;
    }

    private List<ProductionByTime> getProductionByRangeOfDatesGroupedByDuration(OffsetDateTime startDate,
                                                                                OffsetDateTime endDate,
                                                                                Float partitionCoefficient,
                                                                                String duration,
                                                                                String stationClause) {
        // Unreachable through the public API today — every public caller already guards on an empty
        // stationCodes collection before building a non-blank clause. Kept as the actual invariant point
        // so no future caller (public or private) can ever emit an unrestricted query.
        if (stationClause == null || stationClause.isBlank()) {
            return Collections.emptyList();
        }
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT SUM(\"%s\")*%s AS \"%s\" FROM \"%s\" WHERE time >= '%s' AND time <= '%s'%s GROUP BY time(%s)",
                    ProductionPoint.INVERTER_POWER,
                    partitionCoefficient,
                    ProductionPoint.INVERTER_POWER,
                    HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate),
                    stationClause,
                    duration));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, ProductionPoint.class);

            return productionByHourInfluxMapper.mapList(measurementPoints);
        }
    }

    /**
     * Builds an InfluxQL predicate matching any of the given station codes, e.g.
     * {@code ("station_code" = 'A' OR "station_code" = 'B')}.
     */
    private String stationCodeOrChain(Collection<String> stationCodes) {
        return stationCodes.stream()
                .map(code -> String.format("\"%s\" = '%s'", STATION_CODE_TAG, code))
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    /**
     * Same predicate as {@link #stationCodeOrChain} but prefixed with {@code AND } so it can be
     * appended to an existing {@code WHERE time ...} clause.
     */
    private String stationCodeAndClause(Collection<String> stationCodes) {
        return " AND " + stationCodeOrChain(stationCodes);
    }
}
