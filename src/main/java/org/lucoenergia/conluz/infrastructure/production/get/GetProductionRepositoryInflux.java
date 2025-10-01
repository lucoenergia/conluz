package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDuration;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class GetProductionRepositoryInflux implements GetProductionRepository {

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
    public InstantProduction getInstantProduction() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format("SELECT * FROM \"%s\" LIMIT 1",
                    HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT
            ));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, ProductionPoint.class);

            Optional<ProductionPoint> instantProductionPoint = measurementPoints.stream().findFirst();

            if (instantProductionPoint.isPresent()) {
                return instantProductionInfluxMapper.map(instantProductionPoint.get());
            }

            return new InstantProduction(0.0d);
        }
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getHourlyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                    Float partitionCoefficient) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT time, \"%s\"*%s FROM \"%s\" WHERE time >= '%s' AND time <= '%s'",
                    ProductionPoint.INVERTER_POWER,
                    partitionCoefficient,
                    HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, ProductionPoint.class);

            return productionByHourInfluxMapper.mapList(measurementPoints);
        }
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getDailyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                   Float partitionCoefficient) {
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate,
                partitionCoefficient, InfluxDuration.DAILY);
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getMonthlyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                     Float partitionCoefficient) {
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate,
                partitionCoefficient, InfluxDuration.MONTHLY);
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getYearlyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                    Float partitionCoefficient) {
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate,
                partitionCoefficient, InfluxDuration.YEARLY);
    }

    private List<ProductionByTime> getProductionByRangeOfDatesGroupedByDuration(OffsetDateTime startDate,
                                                                                OffsetDateTime endDate,
                                                                                Float partitionCoefficient,
                                                                                String duration) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT SUM(\"%s\")*%s AS \"%s\" FROM \"%s\" WHERE time >= '%s' AND time <= '%s' GROUP BY time(%s)",
                    ProductionPoint.INVERTER_POWER,
                    partitionCoefficient,
                    ProductionPoint.INVERTER_POWER,
                    HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate),
                    duration));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, ProductionPoint.class);

            return productionByHourInfluxMapper.mapList(measurementPoints);
        }
    }
}
