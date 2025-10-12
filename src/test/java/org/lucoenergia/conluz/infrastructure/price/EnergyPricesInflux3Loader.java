package org.lucoenergia.conluz.infrastructure.price;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Profile("test")
@Component
public class EnergyPricesInflux3Loader implements InfluxLoader {

    private static final String FIELD_PRICE1 = "price1";
    /**
     * Time interval is time >= '2023-10-25T00:00:00.000+02:00' and time <= '2023-10-25T23:00:00.000+02:00'
     */
    private static final List<List<Object>> PRICES = Arrays.asList(
            Arrays.asList(1698184800000000000L, 114.1d),
            Arrays.asList(1698188400000000000L, 98.37d),
            Arrays.asList(1698192000000000000L, 70.75d),
            Arrays.asList(1698195600000000000L, 64.75d),
            Arrays.asList(1698199200000000000L, 62.65d),
            Arrays.asList(1698202800000000000L, 61.42d),
            Arrays.asList(1698206400000000000L, 45.59d),
            Arrays.asList(1698210000000000000L, 59.75d),
            Arrays.asList(1698213600000000000L, 65.1d),
            Arrays.asList(1698217200000000000L, 88.59d),
            Arrays.asList(1698220800000000000L, 104.25d),
            Arrays.asList(1698224400000000000L, 95d),
            Arrays.asList(1698228000000000000L, 70.75d),
            Arrays.asList(1698231600000000000L, 62.65d),
            Arrays.asList(1698235200000000000L, 45.48d),
            Arrays.asList(1698238800000000000L, 35d),
            Arrays.asList(1698242400000000000L, 20d),
            Arrays.asList(1698246000000000000L, 17.45d),
            Arrays.asList(1698249600000000000L, 37.77d),
            Arrays.asList(1698253200000000000L, 64.75d),
            Arrays.asList(1698256800000000000L, 85d),
            Arrays.asList(1698260400000000000L, 97.56d),
            Arrays.asList(1698264000000000000L, 109.68d),
            Arrays.asList(1698267600000000000L, 105.97d)
    );

    @Autowired
    private final InfluxDb3ConnectionManager connectionManager;

    public EnergyPricesInflux3Loader(InfluxDb3ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void loadData() {
        InfluxDBClient client = connectionManager.getClient();

        List<Point> points = new ArrayList<>();
        PRICES.forEach(dataPoint -> {
            Long timestampNanos = (Long) dataPoint.get(0);
            Double price = (Double) dataPoint.get(1);

            // Convert nanoseconds to Instant
            Instant timestamp = Instant.ofEpochSecond(0, timestampNanos);

            Point point = Point.measurement(OmieConfig.PRICES_KWH_MEASUREMENT)
                    .setField(FIELD_PRICE1, price)
                    .setTimestamp(timestamp);

            points.add(point);
        });

        // Write all points in a single batch operation
        client.writePoints(points);
    }

    @Override
    public void clearData() {
        // InfluxDB 3 doesn't support DELETE queries in the same way as 1.8
        // For test cleanup, we could drop and recreate the bucket, but for now
        // we'll leave this empty as tests should use isolated buckets or time ranges
    }
}
