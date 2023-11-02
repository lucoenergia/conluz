package org.lucoenergia.conluz.infrastructure.production;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDuration;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.OffsetDateTimeToInfluxDbDateFormatConverter;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class GetProductionRepositoryInflux implements GetProductionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;

    private final InfluxDbConfiguration influxDbConfiguration;

    private final InstantProductionInfluxMapper instantProductionInfluxMapper;
    private final ProductionByHourInfluxMapper productionByHourInfluxMapper;

    public GetProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager, InfluxDbConfiguration influxDbConfiguration, InstantProductionInfluxMapper instantProductionInfluxMapper, ProductionByHourInfluxMapper productionByHourInfluxMapper) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.influxDbConfiguration = influxDbConfiguration;
        this.instantProductionInfluxMapper = instantProductionInfluxMapper;
        this.productionByHourInfluxMapper = productionByHourInfluxMapper;
    }

    @Override
    public InstantProduction getInstantProduction() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query("SELECT * FROM \"energy_production_huawei_hour\" LIMIT 1",
                    influxDbConfiguration.getDatabaseName());

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
                    "SELECT time, \"inverter-power\"*%s FROM \"energy_production_huawei_hour\" WHERE time >= '%s' AND time <= '%s'",
                    partitionCoefficient,
                    OffsetDateTimeToInfluxDbDateFormatConverter.convert(startDate),
                    OffsetDateTimeToInfluxDbDateFormatConverter.convert(endDate)),
                    influxDbConfiguration.getDatabaseName());

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
                    "SELECT SUM(\"inverter-power\")*%s AS \"inverter-power\" FROM \"energy_production_huawei_hour\" WHERE time >= '%s' AND time <= '%s' GROUP BY time(%s)",
                    partitionCoefficient,
                    OffsetDateTimeToInfluxDbDateFormatConverter.convert(startDate),
                    OffsetDateTimeToInfluxDbDateFormatConverter.convert(endDate),
                    duration),
                    influxDbConfiguration.getDatabaseName());

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, ProductionPoint.class);

            return productionByHourInfluxMapper.mapList(measurementPoints);
        }
    }
}
