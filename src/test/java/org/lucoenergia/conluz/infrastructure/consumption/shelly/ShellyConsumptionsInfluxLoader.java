package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Profile("test")
@Component
public class ShellyConsumptionsInfluxLoader implements InfluxLoader {

    public static final String SUPPLY_A_MQTT_PREFIX = "s87sd56df9d9/aaa";
    public static final String SUPPLY_B_MQTT_PREFIX = "s87sd56df9d9/bbb";
    public static final String SUPPLY_C_MQTT_PREFIX = "s87sd56df9d9/ccc";

    /**
     * Time interval is time >= '2023-10-25T00:00:00.000+02:00' and time <= '2023-10-25T23:00:00.000+02:00'
     */
    private static final List INSTANT_CONSUMPTIONS = Arrays.asList(
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
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public ShellyConsumptionsInfluxLoader(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void loadData() {

        try (InfluxDB influxDBConnection = influxDbConnectionManager.getConnection()) {

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            INSTANT_CONSUMPTIONS.stream().forEach(point -> batchPoints.point(Point.measurement(ShellyConfig.CONSUMPTION_KW_MEASUREMENT)
                    .time(((Long) ((List) point).get(0)), TimeUnit.MILLISECONDS)
                    .addField(ShellyInstantConsumptionPoint.CONSUMPTION_KW, convertFromWToKW((Double) ((List) point).get(1)))
                    .tag(ShellyInstantConsumptionPoint.CHANNEL, ((String) ((List) point).get(2)))
                    .tag(ShellyInstantConsumptionPoint.PREFIX, ((String) ((List) point).get(3)))
                    .build()
            ));
            influxDBConnection.write(batchPoints);
        }
    }

    public static Double convertFromWToKW(Double energyInW) {
        return energyInW / 1000;
    }

    @Override
    public void clearData() {
        try (InfluxDB influxDBConnection = influxDbConnectionManager.getConnection()) {
            String query = String.format("DROP SERIES FROM \"%s\"", ShellyConfig.CONSUMPTION_KW_MEASUREMENT);
            influxDBConnection.query(new Query(query));

            query = String.format("DROP SERIES FROM \"%s\"", ShellyConfig.CONSUMPTION_KWH_MEASUREMENT);
            influxDBConnection.query(new Query(query));
        }
    }
}
