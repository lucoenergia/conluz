package org.lucoenergia.conluz.infrastructure.production;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByHour;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
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
    public List<ProductionByHour> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"energy_production_huawei_hour\" WHERE time >= '%s' AND time <= '%s'",
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
}
