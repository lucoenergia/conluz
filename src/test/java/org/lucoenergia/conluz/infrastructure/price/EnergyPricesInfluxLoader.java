package org.lucoenergia.conluz.infrastructure.price;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
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
public class EnergyPricesInfluxLoader implements InfluxLoader {

    private static final String MEASUREMENT = "omie-daily-prices";
    private static final String FIELD_PRICE1 = "price1";
    private static final String FIELD_PRICE2 = "price2";
    /**
     * Time interval is time >= '2023-10-25T00:00:00.000+02:00' and time <= '2023-10-25T23:00:00.000+02:00'
     */
    private static final List PRICES = Arrays.asList(
            Arrays.asList(1698184800000000000L, 114.1d, 114.1d),
            Arrays.asList(1698188400000000000L, 98.37d, 98.37d),
            Arrays.asList(1698192000000000000L, 70.75d, 70.75d),
            Arrays.asList(1698195600000000000L, 64.75d, 64.75d),
            Arrays.asList(1698199200000000000L, 62.65d, 62.65d),
            Arrays.asList(1698202800000000000L, 61.42d, 61.42d),
            Arrays.asList(1698206400000000000L, 45.59d, 45.59d),
            Arrays.asList(1698210000000000000L, 59.75d, 59.75d),
            Arrays.asList(1698213600000000000L, 65.1d, 65.1d),
            Arrays.asList(1698217200000000000L, 88.59d, 88.59d),
            Arrays.asList(1698220800000000000L, 104.25d, 104.25d),
            Arrays.asList(1698224400000000000L, 95d, 95d),
            Arrays.asList(1698228000000000000L, 70.75d, 70.75d),
            Arrays.asList(1698231600000000000L, 62.65d, 62.65d),
            Arrays.asList(1698235200000000000L, 45.48d, 45.48d),
            Arrays.asList(1698238800000000000L, 35d, 35d),
            Arrays.asList(1698242400000000000L, 20d, 20d),
            Arrays.asList(1698246000000000000L, 17.45d, 17.45d),
            Arrays.asList(1698249600000000000L, 37.77d, 37.77d),
            Arrays.asList(1698253200000000000L, 64.75d, 64.75d),
            Arrays.asList(1698256800000000000L, 85d, 85d),
            Arrays.asList(1698260400000000000L, 97.56d, 97.56d),
            Arrays.asList(1698264000000000000L, 109.68d, 109.68d),
            Arrays.asList(1698267600000000000L, 105.97d, 105.97d)
    );

    @Autowired
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public EnergyPricesInfluxLoader(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void loadData() {

        try (InfluxDB influxDBConnection = influxDbConnectionManager.getConnection()) {

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            PRICES.stream().forEach(point -> batchPoints.point(Point.measurement(MEASUREMENT)
                    .time(((Long) ((List) point).get(0)), TimeUnit.NANOSECONDS)
                    .addField(FIELD_PRICE1, ((Double) ((List) point).get(1)))
                    .addField(FIELD_PRICE2, ((Double) ((List) point).get(2)))
                    .build()
            ));
            influxDBConnection.write(batchPoints);
        }
    }

    @Override
    public void clearData() {
    }
}
