package org.lucoenergia.conluz.infrastructure.production;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Profile("test")
@Component
public class EnergyProductionInfluxLoader implements InfluxLoader {

    private static final String MEASUREMENT = HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT;
    private static final String FIELD_INVERTER_POWER = ProductionPoint.INVERTER_POWER;
    /**
     * Time interval is time >= '2023-09-01T00:00:00.000+02:00' and time <= '2023-09-01T23:00:00.000+02:00'
     */
    private static final List PRODUCTION_BY_HOUR = Arrays.asList(
            Arrays.asList(1693519200000000000L, 0d),
            Arrays.asList(1693522800000000000L, 0d),
            Arrays.asList(1693526400000000000L, 0d),
            Arrays.asList(1693530000000000000L, 0d),
            Arrays.asList(1693533600000000000L, 0d),
            Arrays.asList(1693537200000000000L, 0d),
            Arrays.asList(1693540800000000000L, 0d),
            Arrays.asList(1693544400000000000L, 0.13d),
            Arrays.asList(1693548000000000000L, 1.32d),
            Arrays.asList(1693551600000000000L, 5.45d),
            Arrays.asList(1693555200000000000L, 15.97d),
            Arrays.asList(1693558800000000000L, 25.76d),
            Arrays.asList(1693562400000000000L, 27.79d),
            Arrays.asList(1693566000000000000L, 25.29d),
            Arrays.asList(1693569600000000000L, 31.1d),
            Arrays.asList(1693573200000000000L, 26.87d),
            Arrays.asList(1693576800000000000L, 30.95d),
            Arrays.asList(1693580400000000000L, 28.86d),
            Arrays.asList(1693584000000000000L, 10.48d),
            Arrays.asList(1693587600000000000L, 5.37d),
            Arrays.asList(1693591200000000000L, 0.81d),
            Arrays.asList(1693594800000000000L, 0d),
            Arrays.asList(1693598400000000000L, 0d),
            Arrays.asList(1693602000000000000L, 0d)
    );

    @Autowired
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public EnergyProductionInfluxLoader(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void loadData() {

        try (InfluxDB influxDBConnection = influxDbConnectionManager.getConnection()) {

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            PRODUCTION_BY_HOUR.stream().forEach(point -> batchPoints.point(Point.measurement(MEASUREMENT)
                    .time(((Long) ((List) point).get(0)), TimeUnit.NANOSECONDS)
                    .addField(FIELD_INVERTER_POWER, ((Double) ((List) point).get(1)))
                    .build()
            ));
            influxDBConnection.write(batchPoints);
        }
    }

    @Override
    public void clearData() {
    }
}
