package org.lucoenergia.conluz.infrastructure.production.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Seeds Datadis production time-series for two distinct CUPS across hourly, monthly and yearly
 * measurements. Mirrors {@code DatadisConsumptionInfluxLoader} but with the production schema
 * ({@code production_kwh} + {@code obtain_method}, tag {@code cups}).
 *
 * <p>The hourly data for {@link #CUPS_CODE_A} straddles a UTC day boundary (points at 22:00Z and
 * 23:00Z on April 1 vs 00:00Z on April 2) so daily aggregation can be verified to bucket by UTC day.
 */
@Profile("test")
@Component
public class DatadisProductionInfluxLoader implements InfluxLoader {

    private static final String FIELD_PRODUCTION_KWH = "production_kwh";
    private static final String FIELD_OBTAIN_METHOD = "obtain_method";
    private static final String TAG_CUPS = "cups";

    public static final String CUPS_CODE_A = "ES0031406912345678JN0F";
    public static final String CUPS_CODE_B = "ES0031406912345678JN0G";

    /**
     * Hourly production for CUPS A. Format: [timestamp in nanoseconds, production_kwh, obtain_method].
     * April 1 (UTC) buckets to 4.0 kWh (00:00 + 01:00 + 22:00 + 23:00 = 0.5+0.5+1.0+2.0); April 2
     * (UTC) buckets to 4.0 kWh (00:00 + 01:00 = 3.0+1.0). The 23:00Z / 00:00Z pair proves the day
     * boundary is honored.
     */
    private static final List PRODUCTION_BY_HOUR_A = Arrays.asList(
            Arrays.asList(1680307200000000000L, 0.5d, "Real"),  // 2023-04-01T00:00:00Z
            Arrays.asList(1680310800000000000L, 0.5d, "Real"),  // 2023-04-01T01:00:00Z
            Arrays.asList(1680386400000000000L, 1.0d, "Real"),  // 2023-04-01T22:00:00Z
            Arrays.asList(1680390000000000000L, 2.0d, "Real"),  // 2023-04-01T23:00:00Z
            Arrays.asList(1680393600000000000L, 3.0d, "Real"),  // 2023-04-02T00:00:00Z
            Arrays.asList(1680397200000000000L, 1.0d, "Real")   // 2023-04-02T01:00:00Z
    );

    /**
     * Hourly production for CUPS B, a couple of hours on April 1 with a distinct obtain method so the
     * per-CUPS separation of a multi-CUPS query can be asserted.
     */
    private static final List PRODUCTION_BY_HOUR_B = Arrays.asList(
            Arrays.asList(1680307200000000000L, 0.7d, "Estimated"),  // 2023-04-01T00:00:00Z
            Arrays.asList(1680310800000000000L, 0.3d, "Estimated")   // 2023-04-01T01:00:00Z
    );

    /**
     * Monthly aggregated production. Format: [timestamp in nanoseconds, production_kwh, obtain_method].
     * Timestamp is the first day of the month at 00:00:00 UTC.
     */
    private static final List PRODUCTION_BY_MONTH_A = Arrays.asList(
            Arrays.asList(1680307200000000000L, 100.0d, "Real")   // Apr 1, 2023
    );
    private static final List PRODUCTION_BY_MONTH_B = Arrays.asList(
            Arrays.asList(1680307200000000000L, 50.0d, "Estimated")  // Apr 1, 2023
    );

    /**
     * Yearly aggregated production. Timestamp is January 1st at 00:00:00 UTC.
     */
    private static final List PRODUCTION_BY_YEAR_A = Arrays.asList(
            Arrays.asList(1672531200000000000L, 1200.0d, "Real")   // Jan 1, 2023
    );
    private static final List PRODUCTION_BY_YEAR_B = Arrays.asList(
            Arrays.asList(1672531200000000000L, 600.0d, "Estimated")  // Jan 1, 2023
    );

    @Autowired
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public DatadisProductionInfluxLoader(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void loadData() {
        try (InfluxDB influxDBConnection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            load(batchPoints, DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT, CUPS_CODE_A, PRODUCTION_BY_HOUR_A);
            load(batchPoints, DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT, CUPS_CODE_B, PRODUCTION_BY_HOUR_B);
            load(batchPoints, DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT, CUPS_CODE_A, PRODUCTION_BY_MONTH_A);
            load(batchPoints, DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT, CUPS_CODE_B, PRODUCTION_BY_MONTH_B);
            load(batchPoints, DatadisProductionMeasurements.PRODUCTION_KWH_YEAR_MEASUREMENT, CUPS_CODE_A, PRODUCTION_BY_YEAR_A);
            load(batchPoints, DatadisProductionMeasurements.PRODUCTION_KWH_YEAR_MEASUREMENT, CUPS_CODE_B, PRODUCTION_BY_YEAR_B);

            influxDBConnection.write(batchPoints);
        }
    }

    private static void load(BatchPoints batchPoints, String measurement, String cups, List data) {
        data.forEach(point -> {
            List dataPoint = (List) point;
            batchPoints.point(Point.measurement(measurement)
                    .time((Long) dataPoint.get(0), TimeUnit.NANOSECONDS)
                    .tag(TAG_CUPS, cups)
                    .addField(FIELD_PRODUCTION_KWH, (Double) dataPoint.get(1))
                    .addField(FIELD_OBTAIN_METHOD, (String) dataPoint.get(2))
                    .build()
            );
        });
    }

    @Override
    public void clearData() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                    DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT,
                    DatadisProductionMeasurements.PRODUCTION_KWH_YEAR_MEASUREMENT)) {
                for (String cups : List.of(CUPS_CODE_A, CUPS_CODE_B)) {
                    connection.query(new Query(String.format(
                            "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                            measurement, cups)));
                }
            }
        }
    }
}
