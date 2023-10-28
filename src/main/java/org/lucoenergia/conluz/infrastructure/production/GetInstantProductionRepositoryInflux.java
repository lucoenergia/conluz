package org.lucoenergia.conluz.infrastructure.production;

import org.apache.commons.lang3.NotImplementedException;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.GetInstantProductionRepository;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class GetInstantProductionRepositoryInflux implements GetInstantProductionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;

    private final InfluxDbConfiguration influxDbConfiguration;

    private final InstantProductionInfluxMapper mapper;

    public GetInstantProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager, InfluxDbConfiguration influxDbConfiguration, InstantProductionInfluxMapper mapper) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.influxDbConfiguration = influxDbConfiguration;
        this.mapper = mapper;
    }

    @Override
    public InstantProduction getInstantProduction() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query("SELECT * FROM \"energy_production_huawei_hour\" LIMIT 1",
                    influxDbConfiguration.getDatabaseName());

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<InstantProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, InstantProductionPoint.class);

            Optional<InstantProductionPoint> instantProductionPoint = measurementPoints.stream().findFirst();

            if (instantProductionPoint.isPresent()) {
                return mapper.map(instantProductionPoint.get());
            }

            return new InstantProduction(0.0d);
        }
    }
}
