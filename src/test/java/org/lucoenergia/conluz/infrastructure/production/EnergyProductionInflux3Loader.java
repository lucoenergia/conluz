package org.lucoenergia.conluz.infrastructure.production;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
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
public class EnergyProductionInflux3Loader implements InfluxLoader {

    private static final String HOURLY_MEASUREMENT = HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT;
    private static final String REALTIME_MEASUREMENT = HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT;
    private static final String FIELD_INVERTER_POWER = ProductionPoint.INVERTER_POWER;
    private static final String TAG_STATION_CODE = "station_code";
    private static final String STATION_CODE = "NE=12345678";
    /**
     * Time interval is time >= '2023-08-31T22:00:00.000Z' and time <= '2023-09-01T21:00:00.000Z'
     */
    private static final List<List<Object>> PRODUCTION_BY_HOUR = Arrays.asList(
            Arrays.asList(1693512000000000000L, 0d),
            Arrays.asList(1693515600000000000L, 0d),
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

    /**
     * Real-time production data for the same time range
     * Format: [timestamp in nanoseconds, real_health_state, day_power, total_power, day_income, month_power, total_income]
     */
    private static final List<List<Object>> REAL_TIME_PRODUCTION = Arrays.asList(
            Arrays.asList(1693512000000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693515600000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693519200000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693522800000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693526400000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693530000000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693533600000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693537200000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693540800000000000L, 1, 0.0d, 1500.0d, 0.0d, 450.0d, 5000.0d),
            Arrays.asList(1693544400000000000L, 1, 0.13d, 1500.13d, 0.02d, 450.13d, 5000.13d),
            Arrays.asList(1693548000000000000L, 1, 1.32d, 1501.32d, 0.18d, 451.32d, 5001.32d),
            Arrays.asList(1693551600000000000L, 1, 6.77d, 1506.77d, 0.88d, 456.77d, 5006.77d),
            Arrays.asList(1693555200000000000L, 1, 22.74d, 1522.74d, 2.94d, 472.74d, 5022.74d),
            Arrays.asList(1693558800000000000L, 1, 48.50d, 1548.50d, 6.29d, 498.50d, 5048.50d),
            Arrays.asList(1693562400000000000L, 1, 76.29d, 1576.29d, 9.88d, 526.29d, 5076.29d),
            Arrays.asList(1693566000000000000L, 1, 101.58d, 1601.58d, 13.16d, 551.58d, 5101.58d),
            Arrays.asList(1693569600000000000L, 1, 132.68d, 1632.68d, 17.19d, 582.68d, 5132.68d),
            Arrays.asList(1693573200000000000L, 1, 159.55d, 1659.55d, 20.67d, 609.55d, 5159.55d),
            Arrays.asList(1693576800000000000L, 1, 190.50d, 1690.50d, 24.68d, 640.50d, 5190.50d),
            Arrays.asList(1693580400000000000L, 1, 219.36d, 1719.36d, 28.42d, 669.36d, 5219.36d),
            Arrays.asList(1693584000000000000L, 1, 229.84d, 1729.84d, 29.78d, 679.84d, 5229.84d),
            Arrays.asList(1693587600000000000L, 1, 235.21d, 1735.21d, 30.48d, 685.21d, 5235.21d),
            Arrays.asList(1693591200000000000L, 1, 236.02d, 1736.02d, 30.58d, 686.02d, 5236.02d),
            Arrays.asList(1693594800000000000L, 1, 236.02d, 1736.02d, 30.58d, 686.02d, 5236.02d),
            Arrays.asList(1693598400000000000L, 1, 236.02d, 1736.02d, 30.58d, 686.02d, 5236.02d),
            Arrays.asList(1693602000000000000L, 1, 236.02d, 1736.02d, 30.58d, 686.02d, 5236.02d)
    );

    @Autowired
    private final InfluxDb3ConnectionManager connectionManager;

    public EnergyProductionInflux3Loader(InfluxDb3ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void loadData() {
        InfluxDBClient client = connectionManager.getClient();

        List<Point> points = new ArrayList<>();

        // Load hourly production data
        PRODUCTION_BY_HOUR.forEach(dataPoint -> {
            Long timestampNanos = (Long) dataPoint.get(0);
            Double power = (Double) dataPoint.get(1);

            // Convert nanoseconds to Instant
            Instant timestamp = Instant.ofEpochSecond(0, timestampNanos);

            Point point = Point.measurement(HOURLY_MEASUREMENT)
                    .setTag(TAG_STATION_CODE, STATION_CODE)
                    .setField(FIELD_INVERTER_POWER, power)
                    .setTimestamp(timestamp);

            points.add(point);
        });

        // Load real-time production data
        REAL_TIME_PRODUCTION.forEach(dataPoint -> {
            Long timestampNanos = (Long) dataPoint.get(0);
            Integer realHealthState = (Integer) dataPoint.get(1);
            Double dayPower = (Double) dataPoint.get(2);
            Double totalPower = (Double) dataPoint.get(3);
            Double dayIncome = (Double) dataPoint.get(4);
            Double monthPower = (Double) dataPoint.get(5);
            Double totalIncome = (Double) dataPoint.get(6);

            // Convert nanoseconds to Instant
            Instant timestamp = Instant.ofEpochSecond(0, timestampNanos);

            Point point = Point.measurement(REALTIME_MEASUREMENT)
                    .setTag(TAG_STATION_CODE, STATION_CODE)
                    .setField("real_health_state", realHealthState)
                    .setField("day_power", dayPower)
                    .setField("total_power", totalPower)
                    .setField("day_income", dayIncome)
                    .setField("month_power", monthPower)
                    .setField("total_income", totalIncome)
                    .setTimestamp(timestamp);

            points.add(point);
        });

        // Write all points in a single batch operation
        if (!points.isEmpty()) {
            client.writePoints(points);
        }
    }

    @Override
    public void clearData() {
        // InfluxDB 3 doesn't support DELETE queries in the same way as 1.8
        // For test cleanup, we could drop and recreate the bucket, but for now
        // we'll leave this empty as tests should use isolated buckets or time ranges
    }
}
