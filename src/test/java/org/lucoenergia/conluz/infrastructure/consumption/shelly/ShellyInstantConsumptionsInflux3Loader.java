package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Profile("test")
@Component
public class ShellyInstantConsumptionsInflux3Loader implements InfluxLoader {

    public static final String SUPPLY_A_MQTT_PREFIX = "s87sd56df9d9/aaa";
    public static final String SUPPLY_B_MQTT_PREFIX = "s87sd56df9d9/bbb";
    public static final String SUPPLY_C_MQTT_PREFIX = "s87sd56df9d9/ccc";

    /**
     * Time interval is time >= '2023-10-25T00:00:00.000+02:00' and time <= '2023-10-25T23:00:00.000+02:00'
     */
    private static final List<List<Object>> INSTANT_CONSUMPTIONS = Arrays.asList(
            Arrays.asList(toMilliseconds("2023-10-25T00:01:00.000+00:00"), 114.1d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:03:00.000+00:00"), 0d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:05:00.000+00:00"), 98.37d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:07:00.000+00:00"), 12.11d, "1", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:09:00.000+00:00"), 70.75d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:11:00.000+00:00"), 24.75d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:13:00.000+00:00"), 42.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:15:00.000+00:00"), 52.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:17:00.000+00:00"), 662.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:19:00.000+00:00"), 762.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:21:00.000+00:00"), 67.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:23:00.000+00:00"), 65.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:25:00.000+00:00"), 98.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:27:00.000+00:00"), 12.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:29:00.000+00:00"), 33.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:31:00.000+00:00"), 434.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:33:00.000+00:00"), 32.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:35:00.000+00:00"), 345.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:37:00.000+00:00"), 44.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:39:00.000+00:00"), 5.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:41:00.000+00:00"), 6.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:43:00.000+00:00"), 77.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:45:00.000+00:00"), 64.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:47:00.000+00:00"), 78.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:49:00.000+00:00"), 89.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:51:00.000+00:00"), 76.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:53:00.000+00:00"), 11.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:55:00.000+00:00"), 32.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:57:00.000+00:00"), 556.65d, "0", SUPPLY_A_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:59:00.000+00:00"), 455.65d, "0", SUPPLY_A_MQTT_PREFIX),

            Arrays.asList(toMilliseconds("2023-10-25T00:00:00.000+00:00"), 61.42d, "0", SUPPLY_B_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T01:00:00.000+00:00"), 45.59d, "0", SUPPLY_B_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T02:00:00.000+00:00"), 59.75d, "0", SUPPLY_B_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T03:00:00.000+00:00"), 65.1d, "0", SUPPLY_B_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T04:00:00.000+00:00"), 88.59d, "0", SUPPLY_B_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T05:00:00.000+00:00"), 104.25d, "0", SUPPLY_B_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T06:00:00.000+00:00"), 95d, "0", SUPPLY_B_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T07:00:00.000+00:00"), 70.75d, "0", SUPPLY_B_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T08:00:00.000+00:00"), 62.65d, "0", SUPPLY_B_MQTT_PREFIX),

            Arrays.asList(toMilliseconds("2023-10-25T00:00:00.000+00:00"), 45.48d, "1", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T00:00:00.000+00:00"), 35d, "0", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T01:00:00.000+00:00"), 20d, "1", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T02:00:00.000+00:00"), 17.45d, "1", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T03:00:00.000+00:00"), 37.77d, "1", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T04:00:00.000+00:00"), 64.75d, "1", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T05:00:00.000+00:00"), 85d, "1", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T06:00:00.000+00:00"), 97.56d, "1", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T07:00:00.000+00:00"), 109.68d, "1", SUPPLY_C_MQTT_PREFIX),
            Arrays.asList(toMilliseconds("2023-10-25T07:00:00.000+00:00"), 105.97d, "0", SUPPLY_C_MQTT_PREFIX)
    );

    private static long toMilliseconds(String date) {
        return OffsetDateTime.parse(date).toInstant().toEpochMilli();
    }

    @Autowired
    private final InfluxDb3ConnectionManager connectionManager;

    public ShellyInstantConsumptionsInflux3Loader(InfluxDb3ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void loadData() {
        InfluxDBClient client = connectionManager.getClient();

        List<Point> points = new ArrayList<>();
        INSTANT_CONSUMPTIONS.forEach(dataPoint -> {
            Long timestampMillis = (Long) dataPoint.get(0);
            Double powerW = (Double) dataPoint.get(1);
            String channel = (String) dataPoint.get(2);
            String prefix = (String) dataPoint.get(3);

            // Convert milliseconds to Instant
            Instant timestamp = Instant.ofEpochMilli(timestampMillis);

            Point point = Point.measurement(ShellyInstantConsumptionPoint.MEASUREMENT)
                    .setTag(ShellyInstantConsumptionPoint.CHANNEL, channel)
                    .setTag(ShellyInstantConsumptionPoint.PREFIX, prefix)
                    .setField(ShellyInstantConsumptionPoint.CONSUMPTION_KW, convertFromWToKW(powerW))
                    .setTimestamp(timestamp);

            points.add(point);
        });

        // Write all points in a single batch operation
        client.writePoints(points);
    }

    public static Double convertFromWToKW(Double energyInW) {
        return energyInW / 1000;
    }

    @Override
    public void clearData() {
        // InfluxDB 3 doesn't support DELETE queries in the same way as 1.8
        // For test cleanup, we could drop and recreate the bucket, but for now
        // we'll leave this empty as tests should use isolated buckets or time ranges
    }
}
