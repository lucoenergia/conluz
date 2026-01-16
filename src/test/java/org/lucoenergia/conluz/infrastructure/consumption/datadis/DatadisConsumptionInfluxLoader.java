package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
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
public class DatadisConsumptionInfluxLoader implements InfluxLoader {

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
    private static final List CONSUMPTION_BY_HOUR = Arrays.asList(
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

    /**
     * Monthly aggregated consumption data spanning 2023-2024
     * Format: [timestamp in nanoseconds, consumption_kwh, surplus_energy_kwh, self_consumption_energy_kwh, obtain_method]
     * Each timestamp represents the first day of the month at 00:00:00 UTC
     */
    private static final List CONSUMPTION_BY_MONTH = Arrays.asList(
            // 2023
            Arrays.asList(1672531200000000000L, 450.5d, 25.0d, 85.0d, "Real"),  // Jan 1, 2023 - Winter (high consumption, low solar)
            Arrays.asList(1675209600000000000L, 420.3d, 30.0d, 90.0d, "Real"),  // Feb 1, 2023
            Arrays.asList(1677628800000000000L, 380.7d, 45.0d, 110.0d, "Real"), // Mar 1, 2023
            Arrays.asList(1680307200000000000L, 330.2d, 65.0d, 135.0d, "Real"), // Apr 1, 2023 - Spring
            Arrays.asList(1682899200000000000L, 290.8d, 85.0d, 155.0d, "Real"), // May 1, 2023
            Arrays.asList(1685577600000000000L, 270.5d, 95.0d, 165.0d, "Real"), // Jun 1, 2023 - Summer (low consumption, high solar)
            Arrays.asList(1688169600000000000L, 285.3d, 98.0d, 168.0d, "Real"), // Jul 1, 2023
            Arrays.asList(1690848000000000000L, 295.6d, 92.0d, 162.0d, "Real"), // Aug 1, 2023
            Arrays.asList(1693526400000000000L, 310.4d, 75.0d, 145.0d, "Real"), // Sep 1, 2023 - Fall
            Arrays.asList(1696118400000000000L, 340.8d, 55.0d, 120.0d, "Real"), // Oct 1, 2023
            Arrays.asList(1698796800000000000L, 390.5d, 35.0d, 95.0d, "Real"),  // Nov 1, 2023
            Arrays.asList(1701388800000000000L, 440.2d, 28.0d, 88.0d, "Real"),  // Dec 1, 2023 - Winter

            // 2024
            Arrays.asList(1704067200000000000L, 455.8d, 26.0d, 86.0d, "Real"),  // Jan 1, 2024 - Winter
            Arrays.asList(1706745600000000000L, 425.6d, 32.0d, 92.0d, "Real"),  // Feb 1, 2024
            Arrays.asList(1709251200000000000L, 385.3d, 48.0d, 112.0d, "Real"), // Mar 1, 2024
            Arrays.asList(1711929600000000000L, 335.7d, 68.0d, 138.0d, "Real"), // Apr 1, 2024 - Spring
            Arrays.asList(1714521600000000000L, 295.2d, 88.0d, 158.0d, "Real"), // May 1, 2024
            Arrays.asList(1717200000000000000L, 275.8d, 97.0d, 167.0d, "Real"), // Jun 1, 2024 - Summer
            Arrays.asList(1719792000000000000L, 290.5d, 100.0d, 170.0d, "Real"),// Jul 1, 2024
            Arrays.asList(1722470400000000000L, 300.3d, 94.0d, 164.0d, "Real"), // Aug 1, 2024
            Arrays.asList(1725148800000000000L, 315.6d, 78.0d, 148.0d, "Real"), // Sep 1, 2024 - Fall
            Arrays.asList(1727740800000000000L, 345.2d, 58.0d, 122.0d, "Real"), // Oct 1, 2024
            Arrays.asList(1730419200000000000L, 395.8d, 38.0d, 98.0d, "Real"),  // Nov 1, 2024
            Arrays.asList(1733011200000000000L, 445.5d, 30.0d, 90.0d, "Real")   // Dec 1, 2024 - Winter
    );

    /**
     * Yearly aggregated consumption data spanning 2023-2024
     * Format: [timestamp in nanoseconds, consumption_kwh, surplus_energy_kwh, self_consumption_energy_kwh, obtain_method]
     * Each timestamp represents January 1st of the year at 00:00:00 UTC
     */
    private static final List CONSUMPTION_BY_YEAR = Arrays.asList(
            // Sum of all 2023 monthly values: 4205.3 kWh consumption, 726.0 kWh surplus, 1453.0 kWh self-consumption
            Arrays.asList(1672531200000000000L, 4205.3d, 726.0d, 1453.0d, "Real"),  // Jan 1, 2023
            // Sum of all 2024 monthly values: 4320.3 kWh consumption, 777.0 kWh surplus, 1535.0 kWh self-consumption
            Arrays.asList(1704067200000000000L, 4320.3d, 777.0d, 1535.0d, "Real")   // Jan 1, 2024
    );

    @Autowired
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public DatadisConsumptionInfluxLoader(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void loadData() {
        try (InfluxDB influxDBConnection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            loadConsumptionsByHour(batchPoints);
            loadConsumptionsByMonth(batchPoints);
            loadConsumptionsByYear(batchPoints);

            influxDBConnection.write(batchPoints);
        }
    }

    private static void loadConsumptionsByHour(BatchPoints batchPoints) {
        CONSUMPTION_BY_HOUR.forEach(point -> {
            List dataPoint = (List) point;
            batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT)
                    .time((Long) dataPoint.get(0), TimeUnit.NANOSECONDS)
                    .tag(TAG_CUPS, CUPS_CODE)
                    .addField(FIELD_CONSUMPTION_KWH, (Double) dataPoint.get(1))
                    .addField(FIELD_SURPLUS_ENERGY_KWH, (Double) dataPoint.get(2))
                    .addField(FIELD_SELF_CONSUMPTION_ENERGY_KWH, (Double) dataPoint.get(3))
                    .addField(FIELD_OBTAIN_METHOD, (String) dataPoint.get(4))
                    .build()
            );
        });
    }

    private static void loadConsumptionsByMonth(BatchPoints batchPoints) {
        CONSUMPTION_BY_MONTH.forEach(point -> {
            List dataPoint = (List) point;
            batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT)
                    .time((Long) dataPoint.get(0), TimeUnit.NANOSECONDS)
                    .tag(TAG_CUPS, CUPS_CODE)
                    .addField(FIELD_CONSUMPTION_KWH, (Double) dataPoint.get(1))
                    .addField(FIELD_SURPLUS_ENERGY_KWH, (Double) dataPoint.get(2))
                    .addField(FIELD_SELF_CONSUMPTION_ENERGY_KWH, (Double) dataPoint.get(3))
                    .addField(FIELD_OBTAIN_METHOD, (String) dataPoint.get(4))
                    .build()
            );
        });
    }

    private static void loadConsumptionsByYear(BatchPoints batchPoints) {
        CONSUMPTION_BY_YEAR.forEach(point -> {
            List dataPoint = (List) point;
            batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT)
                    .time((Long) dataPoint.get(0), TimeUnit.NANOSECONDS)
                    .tag(TAG_CUPS, CUPS_CODE)
                    .addField(FIELD_CONSUMPTION_KWH, (Double) dataPoint.get(1))
                    .addField(FIELD_SURPLUS_ENERGY_KWH, (Double) dataPoint.get(2))
                    .addField(FIELD_SELF_CONSUMPTION_ENERGY_KWH, (Double) dataPoint.get(3))
                    .addField(FIELD_OBTAIN_METHOD, (String) dataPoint.get(4))
                    .build()
            );
        });
    }

    @Override
    public void clearData() {
        // InfluxDB test container is recreated for each test, so no need to clear data
    }
}
