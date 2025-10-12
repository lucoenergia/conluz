package org.lucoenergia.conluz.infrastructure.consumption.shelly.persist;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.PersistShellyConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Qualifier("persistShellyConsumptionRepositoryInflux3")
public class PersistShellyConsumptionRepositoryInflux3 implements PersistShellyConsumptionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistShellyConsumptionRepositoryInflux3.class);

    private final InfluxDb3ConnectionManager connectionManager;

    public PersistShellyConsumptionRepositoryInflux3(InfluxDb3ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void persistInstantConsumptions(List<ShellyInstantConsumption> consumptions) {
        LOGGER.debug("Persisting Shelly instant consumptions data {}", consumptions);

        InfluxDBClient client = connectionManager.getClient();

        try {
            List<Point> points = new ArrayList<>();
            for (ShellyInstantConsumption consumption : consumptions) {
                Point point = Point.measurement(ShellyInstantConsumptionPoint.MEASUREMENT)
                        .setTag(ShellyInstantConsumptionPoint.PREFIX, consumption.getPrefix())
                        .setTag(ShellyInstantConsumptionPoint.CHANNEL, consumption.getChannel())
                        .setField(ShellyInstantConsumptionPoint.CONSUMPTION_KW, consumption.getConsumptionKW())
                        .setTimestamp(consumption.getTimestamp());

                points.add(point);
            }
            if (!points.isEmpty()) {
                client.writePoints(points);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to persist Shelly instant consumptions", e);
        }
    }

    @Override
    public void persistConsumptions(List<ShellyConsumption> consumptions) {
        LOGGER.debug("Persisting Shelly consumptions data {}", consumptions);

        InfluxDBClient client = connectionManager.getClient();

        try {
            List<Point> points = new ArrayList<>();
            for (ShellyConsumption consumption : consumptions) {
                Point point = Point.measurement(ShellyConsumptionPoint.MEASUREMENT)
                        .setTag(ShellyConsumptionPoint.PREFIX, consumption.getPrefix())
                        .setField(ShellyConsumptionPoint.CONSUMPTION_KWH, consumption.getConsumptionKWh())
                        .setTimestamp(consumption.getTimestamp());

                points.add(point);
            }
            if (!points.isEmpty()) {
                client.writePoints(points);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to persist Shelly consumptions", e);
        }
    }
}
