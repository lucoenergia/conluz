package org.lucoenergia.conluz.infrastructure.production.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.PersistDatadisProductionRepository;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class PersistDatadisProductionRepositoryInflux implements PersistDatadisProductionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public PersistDatadisProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                    DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public void persistHourlyProductions(List<DatadisProduction> productions) {
        persistProductions(productions, DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT);
    }

    private void persistProductions(List<DatadisProduction> productions, String measurementName) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (DatadisProduction production : productions) {
                Point point = Point.measurement(measurementName)
                        .time(dateConverter.convertStringDateToMilliseconds(mergeDateAndTime(production)), TimeUnit.MILLISECONDS)
                        .tag("cups", production.getCups())
                        .addField("production_kwh", production.getProductionKWh())
                        .addField("obtain_method", production.getObtainMethod())
                        .build();

                batchPoints.point(point);
            }
            connection.write(batchPoints);
        }
    }

    private String mergeDateAndTime(DatadisProduction production) {
        return String.format("%sT%s", production.getDate(), production.getTime());
    }
}
