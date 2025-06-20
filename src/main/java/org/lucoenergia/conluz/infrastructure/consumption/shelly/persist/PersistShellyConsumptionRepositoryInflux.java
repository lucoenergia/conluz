package org.lucoenergia.conluz.infrastructure.consumption.shelly.persist;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.PersistShellyConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class PersistShellyConsumptionRepositoryInflux implements PersistShellyConsumptionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistShellyConsumptionRepositoryInflux.class);

    private final InfluxDbConnectionManager influxDbConnectionManager;

    public PersistShellyConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void persistInstantConsumptions(List<ShellyInstantConsumption> consumptions) {

        LOGGER.debug("Persisting Shelly instant consumptions data {}", consumptions);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Create new batch points object
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (ShellyInstantConsumption consumption : consumptions) {
                Point point = Point.measurement(ShellyInstantConsumptionPoint.MEASUREMENT)
                        .time(consumption.getTimestamp().toEpochMilli(), TimeUnit.MILLISECONDS)
                        .tag(ShellyInstantConsumptionPoint.PREFIX, consumption.getPrefix())
                        .tag(ShellyInstantConsumptionPoint.CHANNEL, consumption.getChannel())
                        .addField(ShellyInstantConsumptionPoint.CONSUMPTION_KW, consumption.getConsumptionKW())
                        .build();

                batchPoints.point(point);
            }
            connection.write(batchPoints);
        } catch (Exception e) {
            LOGGER.error("Unable to persist Shelly instant consumptions", e);
        }
    }

    @Override
    public void persistConsumptions(List<ShellyConsumption> consumptions) {

        LOGGER.debug("Persisting Shelly consumptions data {}", consumptions);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Create new batch points object
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (ShellyConsumption consumption : consumptions) {
                Point point = Point.measurement(ShellyConsumptionPoint.MEASUREMENT)
                        .time(consumption.getTimestamp().toEpochMilli(), TimeUnit.MILLISECONDS)
                        .tag(ShellyConsumptionPoint.PREFIX, consumption.getPrefix())
                        .addField(ShellyConsumptionPoint.CONSUMPTION_KWH, consumption.getConsumptionKWh())
                        .build();

                batchPoints.point(point);
            }
            connection.write(batchPoints);
        } catch (Exception e) {
            LOGGER.error("Unable to persist Shelly consumptions", e);
        }
    }
}
