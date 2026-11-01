package org.lucoenergia.conluz.infrastructure.datadis.persist;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class PersistDatadisConsumptionRepositoryInflux implements PersistDatadisConsumptionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;

    public PersistDatadisConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void persistHourlyConsumptions(List<DatadisHourlyRecord<DatadisConsumption>> consumptions) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (DatadisHourlyRecord<DatadisConsumption> hourlyRecord : consumptions) {
                DatadisConsumption consumption = hourlyRecord.record();
                Point point = Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT)
                        .time(hourlyRecord.instant().toEpochMilli(), TimeUnit.MILLISECONDS)
                        .tag("cups", consumption.getCups())
                        .addField("consumption_kwh", consumption.getConsumptionKWh())
                        .addField("obtain_method", consumption.getObtainMethod())
                        .addField("surplus_energy_kwh", consumption.getSurplusEnergyKWh())
                        .addField("generation_energy_kwh", consumption.getGenerationEnergyKWh())
                        .addField("self_consumption_energy_kwh", consumption.getSelfConsumptionEnergyKWh())
                        .build();

                batchPoints.point(point);
            }
            connection.write(batchPoints);
        }
    }
}
