package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionDay;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshot;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reads the real-time snapshots the Huawei sync stores, one station at a time, in a single request of two
 * statements: the last {@code day_power} of the previous local day, and today's snapshots themselves, at most about
 * 288 of them. Both intervals are half-open, so a snapshot taken exactly at local midnight belongs to the new day.
 */
@Repository
public class GetHuaweiProductionRepositoryInflux implements GetHuaweiProductionRepository {

    private static final String COLUMN_TIME = "time";
    private static final String COLUMN_DAY_POWER = "day_power";

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetHuaweiProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                               DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public InverterProductionDay getProductionDay(String stationCode, Instant previousDayStart, Instant todayStart,
                                                  Instant now) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    """
                            SELECT LAST("day_power") AS "day_power"
                            FROM "%1$s"
                            WHERE station_code = '%2$s'
                                AND time >= '%3$s'
                                AND time < '%4$s';
                            SELECT "day_power"
                            FROM "%1$s"
                            WHERE station_code = '%2$s'
                                AND time >= '%4$s'
                                AND time < '%5$s'
                            ORDER BY time ASC
                            """,
                    HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT,
                    escape(stationCode),
                    dateConverter.convertToString(previousDayStart),
                    dateConverter.convertToString(todayStart),
                    dateConverter.convertToString(now)));

            // Times in milliseconds come back as a number, whatever the server's precision.
            QueryResult queryResult = connection.query(query, TimeUnit.MILLISECONDS);
            List<InverterSnapshot> previousDay = snapshots(queryResult, 0);
            List<InverterSnapshot> today = snapshots(queryResult, 1);
            return new InverterProductionDay(
                    previousDay.isEmpty() ? null : previousDay.get(0).dayPowerKWh(),
                    today);
        }
    }

    /**
     * The rows of one statement's result that carry a {@code day_power}, in the order they come back. A statement
     * that matched nothing has no series.
     */
    private List<InverterSnapshot> snapshots(QueryResult queryResult, int statement) {
        List<InverterSnapshot> snapshots = new ArrayList<>();
        if (queryResult == null || queryResult.getResults() == null
                || queryResult.getResults().size() <= statement) {
            return snapshots;
        }
        QueryResult.Result result = queryResult.getResults().get(statement);
        if (result.getSeries() == null) {
            return snapshots;
        }
        for (QueryResult.Series series : result.getSeries()) {
            int timeIndex = series.getColumns().indexOf(COLUMN_TIME);
            int dayPowerIndex = series.getColumns().indexOf(COLUMN_DAY_POWER);
            for (List<Object> row : series.getValues()) {
                if (row.get(dayPowerIndex) instanceof Number dayPower) {
                    snapshots.add(new InverterSnapshot(
                            dateConverter.convertMillisecondsToInstant(((Number) row.get(timeIndex)).longValue()),
                            dayPower.doubleValue()));
                }
            }
        }
        return snapshots;
    }

    /**
     * The station code as the content of an InfluxQL string literal. The provider code is free text, so a quote in it
     * must not end the literal.
     */
    private static String escape(String stationCode) {
        return stationCode.replace("\\", "\\\\").replace("'", "\\'");
    }
}
