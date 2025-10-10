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
public class ShellyMqttPowerMessagesInflux3Loader implements InfluxLoader {

    /**
     * Time interval is time >= '2023-10-25T00:00:00.000+02:00' and time <= '2023-10-25T23:00:00.000+02:00'
     */
    private static final List<List<Object>> MQTT_POWER_MESSAGES = Arrays.asList(
            Arrays.asList(toMilliseconds("2023-10-25T00:01:00.000+00:00"), 114.1d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:03:00.000+00:00"), 0d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:05:00.000+00:00"), 98.37d, "shellies/70c590f9f395fbae/mariajesus/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:07:00.000+00:00"), 12.11d, "shellies/70c590f9f395fbae/mariajesus/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:09:00.000+00:00"), 70.75d, "shellies/70c590f9f395fbae/consultorio/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:11:00.000+00:00"), 24.75d, "shellies/70c590f9f395fbae/consultorio/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:13:00.000+00:00"), 42.65d, "shellies/70c590f9f395fbae/bar/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:15:00.000+00:00"), 52.65d, "shellies/70c590f9f395fbae/bar/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:17:00.000+00:00"), 662.65d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:19:00.000+00:00"), 762.65d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:21:00.000+00:00"), 67.65d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:23:00.000+00:00"), 65.65d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:25:00.000+00:00"), 98.65d, "shellies/70c590f9f395fbae/bea/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:27:00.000+00:00"), 12.65d, "shellies/70c590f9f395fbae/bea/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:29:00.000+00:00"), 33.65d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:31:00.000+00:00"), 434.65d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:33:00.000+00:00"), 32.65d, "shellies/70c590f9f395fbae/paquita/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:35:00.000+00:00"), 345.65d, "shellies/70c590f9f395fbae/paquita/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:37:00.000+00:00"), 44.65d, "shellies/70c590f9f395fbae/bea/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:39:00.000+00:00"), 5.65d, "shellies/70c590f9f395fbae/bea/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:41:00.000+00:00"), 6.65d, "shellies/70c590f9f395fbae/bar/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:43:00.000+00:00"), 77.65d, "shellies/70c590f9f395fbae/bar/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:45:00.000+00:00"), 64.65d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:47:00.000+00:00"), 78.65d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:49:00.000+00:00"), 89.65d, "shellies/70c590f9f395fbae/mnu/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:51:00.000+00:00"), 76.65d, "shellies/70c590f9f395fbae/mnu/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:53:00.000+00:00"), 11.65d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:55:00.000+00:00"), 32.65d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:57:00.000+00:00"), 556.65d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T00:59:00.000+00:00"), 455.65d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T01:00:00.000+00:00"), 45.59d, "shellies/70c590f9f395fbae/mariajesus/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T02:00:00.000+00:00"), 59.75d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T03:00:00.000+00:00"), 65.1d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T04:00:00.000+00:00"), 88.59d, "shellies/70c590f9f395fbae/consultorio/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T05:00:00.000+00:00"), 104.25d, "shellies/70c590f9f395fbae/consultorio/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T06:00:00.000+00:00"), 95d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T07:00:00.000+00:00"), 70.75d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T08:00:00.000+00:00"), 62.65d, "shellies/70c590f9f395fbae/paquita/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T01:00:00.000+00:00"), 20d, "shellies/70c590f9f395fbae/bea/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T02:00:00.000+00:00"), 17.45d, "shellies/70c590f9f395fbae/vcm/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T03:00:00.000+00:00"), 37.77d, "shellies/70c590f9f395fbae/vcm/emeter/1/power"),
            Arrays.asList(toMilliseconds("2023-10-25T04:00:00.000+00:00"), 64.75d, "shellies/70c590f9f395fbae/bar/emeter/0/power"),
            Arrays.asList(toMilliseconds("2023-10-25T05:00:00.000+00:00"), 85d, "shellies/70c590f9f395fbae/bar/emeter/1/power")
    );

    private static long toMilliseconds(String date) {
        return OffsetDateTime.parse(date).toInstant().toEpochMilli();
    }

    @Autowired
    private final InfluxDb3ConnectionManager connectionManager;

    public ShellyMqttPowerMessagesInflux3Loader(InfluxDb3ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void loadData() {
        InfluxDBClient client = connectionManager.getClient();

        List<Point> points = new ArrayList<>();
        MQTT_POWER_MESSAGES.forEach(dataPoint -> {
            Long timestampMillis = (Long) dataPoint.get(0);
            Double value = (Double) dataPoint.get(1);
            String topic = (String) dataPoint.get(2);

            // Convert milliseconds to Instant
            Instant timestamp = Instant.ofEpochMilli(timestampMillis);

            Point point = Point.measurement(ShellyMqttPowerMessagePoint.MEASUREMENT)
                    .setField(ShellyMqttPowerMessagePoint.VALUE, value)
                    .setField(ShellyMqttPowerMessagePoint.TOPIC, topic)
                    .setField("host", "62975a4472fd")
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
