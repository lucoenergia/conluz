package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
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
public class DatadisConsumptionInflux3Loader implements InfluxLoader {

    private static final String MEASUREMENT = DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT;
    private static final String FIELD_CONSUMPTION_KWH = "consumption_kwh";
    private static final String FIELD_SURPLUS_ENERGY_KWH = "surplus_energy_kwh";
    private static final String FIELD_SELF_CONSUMPTION_ENERGY_KWH = "self_consumption_energy_kwh";
    private static final String FIELD_OBTAIN_METHOD = "obtain_method";
    private static final String TAG_CUPS = "cups";
    private static final String CUPS_CODE = "ES0031406912345678JN0F";

    /**
     * Time interval is time >= '2023-04-01T00:00:00.000Z' and time <= '2023-04-30T23:00:00.000Z'
     * Data represents hourly consumption for April 2023
     * Format: [timestamp in nanoseconds, consumption_kwh, surplus_energy_kwh, self_consumption_energy_kwh, obtain_method]
     */
    private static final List<List<Object>> CONSUMPTION_BY_HOUR = Arrays.asList(
            // April 1, 2023 - first 24 hours
            Arrays.asList(1680307200000000000L, 0.45d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680310800000000000L, 0.42d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680314400000000000L, 0.38d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680318000000000000L, 0.35d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680321600000000000L, 0.33d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680325200000000000L, 0.32d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680328800000000000L, 0.40d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680332400000000000L, 0.55d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680336000000000000L, 0.68d, 0.10d, 0.15d, "Real"),
            Arrays.asList(1680339600000000000L, 0.72d, 0.20d, 0.25d, "Real"),
            Arrays.asList(1680343200000000000L, 0.75d, 0.25d, 0.30d, "Real"),
            Arrays.asList(1680346800000000000L, 0.78d, 0.30d, 0.35d, "Real"),
            Arrays.asList(1680350400000000000L, 0.80d, 0.35d, 0.40d, "Real"),
            Arrays.asList(1680354000000000000L, 0.76d, 0.28d, 0.33d, "Real"),
            Arrays.asList(1680357600000000000L, 0.70d, 0.22d, 0.27d, "Real"),
            Arrays.asList(1680361200000000000L, 0.65d, 0.15d, 0.20d, "Real"),
            Arrays.asList(1680364800000000000L, 0.58d, 0.08d, 0.12d, "Real"),
            Arrays.asList(1680368400000000000L, 0.50d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680372000000000000L, 0.55d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680375600000000000L, 0.60d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680379200000000000L, 0.58d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680382800000000000L, 0.52d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680386400000000000L, 0.48d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680390000000000000L, 0.46d, 0.0d, 0.0d, "Real"),
            // April 2, 2023 - sample data
            Arrays.asList(1680393600000000000L, 0.44d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680397200000000000L, 0.40d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680400800000000000L, 0.37d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680404400000000000L, 0.36d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680408000000000000L, 0.34d, 0.0d, 0.0d, "Real"),
            Arrays.asList(1680411600000000000L, 0.35d, 0.0d, 0.0d, "Real")
    );

    @Autowired
    private final InfluxDb3ConnectionManager connectionManager;

    public DatadisConsumptionInflux3Loader(InfluxDb3ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void loadData() {
        InfluxDBClient client = connectionManager.getClient();

        List<Point> points = new ArrayList<>();

        CONSUMPTION_BY_HOUR.forEach(dataPoint -> {
            Long timestampNanos = (Long) dataPoint.get(0);
            Double consumptionKwh = (Double) dataPoint.get(1);
            Double surplusEnergyKwh = (Double) dataPoint.get(2);
            Double selfConsumptionEnergyKwh = (Double) dataPoint.get(3);
            String obtainMethod = (String) dataPoint.get(4);

            // Convert nanoseconds to Instant
            Instant timestamp = Instant.ofEpochSecond(0, timestampNanos);

            Point point = Point.measurement(MEASUREMENT)
                    .setTag(TAG_CUPS, CUPS_CODE)
                    .setField(FIELD_CONSUMPTION_KWH, consumptionKwh)
                    .setField(FIELD_SURPLUS_ENERGY_KWH, surplusEnergyKwh)
                    .setField(FIELD_SELF_CONSUMPTION_ENERGY_KWH, selfConsumptionEnergyKwh)
                    .setField(FIELD_OBTAIN_METHOD, obtainMethod)
                    .setTimestamp(timestamp);

            points.add(point);
        });

        client.writePoints(points);
    }

    @Override
    public void clearData() {
        // InfluxDB 3 Core doesn't support DELETE queries in the same way as 1.8
        // For test cleanup, we could drop and recreate the database, but for now
        // we'll leave this empty as tests should use isolated time ranges
    }
}
