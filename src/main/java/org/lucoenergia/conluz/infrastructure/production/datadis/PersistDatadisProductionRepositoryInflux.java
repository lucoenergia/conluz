package org.lucoenergia.conluz.infrastructure.production.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.PersistDatadisProductionRepository;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class PersistDatadisProductionRepositoryInflux implements PersistDatadisProductionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;

    public PersistDatadisProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void persistHourlyProductions(List<DatadisHourlyRecord<DatadisProduction>> productions) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (DatadisHourlyRecord<DatadisProduction> hourlyRecord : productions) {
                DatadisProduction production = hourlyRecord.record();
                Point point = Point.measurement(DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT)
                        .time(hourlyRecord.instant().toEpochMilli(), TimeUnit.MILLISECONDS)
                        .tag("cups", production.getCups())
                        .addField("production_kwh", production.getProductionKWh())
                        .addField("obtain_method", production.getObtainMethod())
                        .build();

                batchPoints.point(point);
            }
            connection.write(batchPoints);
        }
    }
}
