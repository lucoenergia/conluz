package org.lucoenergia.conluz.infrastructure.consumption.datadis.metrics;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Aggregates a supply's hourly consumption records inside InfluxDB. The sums are pushed down to
 * the store so the memory cost of a query is constant, whatever the length of the period.
 *
 * <p>Results are read straight from the {@link QueryResult} rather than through
 * {@code InfluxDBResultMapper}, because three distinct degenerate shapes have to be told apart and
 * all of them must produce zeros rather than a failure:</p>
 * <ul>
 *     <li>no series at all, when no record matches the predicate;</li>
 *     <li>a column missing from the response, when the field is not part of the measurement's
 *     schema (a deployment where no supply has ever reported self-consumption);</li>
 *     <li>a column present with a null value, when the field exists on the measurement but not on
 *     the records of this supply.</li>
 * </ul>
 */
@Repository
public class GetDatadisConsumptionAggregateRepositoryInflux implements GetDatadisConsumptionAggregateRepository {

    private static final String COLUMN_TIME = "time";
    private static final String COLUMN_CONSUMPTION_KWH = "consumption_kwh";
    private static final String COLUMN_SELF_CONSUMPTION_ENERGY_KWH = "self_consumption_energy_kwh";
    private static final String COLUMN_SURPLUS_ENERGY_KWH = "surplus_energy_kwh";
    private static final String COLUMN_HOURS_WITH_DATA = "hours_with_data";

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetDatadisConsumptionAggregateRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                          DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public DatadisConsumptionAggregate aggregateByRangeOfDates(Supply supply, OffsetDateTime startDate,
                                                                OffsetDateTime endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Both bounds are inclusive, matching the sibling consumption endpoints.
            Query query = new Query(String.format(
                    """
                            SELECT
                                SUM("consumption_kwh") AS "consumption_kwh",
                                SUM("self_consumption_energy_kwh") AS "self_consumption_energy_kwh",
                                SUM("surplus_energy_kwh") AS "surplus_energy_kwh",
                                COUNT("consumption_kwh") AS "hours_with_data"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time <= '%s'
                            """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult.Series series = firstSeries(connection.query(query));
            if (series == null) {
                return DatadisConsumptionAggregate.empty();
            }
            List<Object> row = series.getValues().get(0);

            return new DatadisConsumptionAggregate(
                    readDouble(series, row, COLUMN_CONSUMPTION_KWH),
                    readDouble(series, row, COLUMN_SELF_CONSUMPTION_ENERGY_KWH),
                    readDouble(series, row, COLUMN_SURPLUS_ENERGY_KWH),
                    (long) readDouble(series, row, COLUMN_HOURS_WITH_DATA));
        }
    }

    @Override
    public Optional<RecordedConsumptionPeriod> findRecordedPeriod(Supply supply) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // FIRST and LAST must be queried separately: a row carrying two selectors cannot also
            // carry their two different timestamps, and InfluxDB reports the epoch instead.
            Instant firstRecord = selectorTime(connection, "FIRST", supply);
            Instant lastRecord = selectorTime(connection, "LAST", supply);

            if (firstRecord == null || lastRecord == null) {
                return Optional.empty();
            }
            return Optional.of(new RecordedConsumptionPeriod(firstRecord, lastRecord));
        }
    }

    private Instant selectorTime(InfluxDB connection, String selector, Supply supply) {
        Query query = new Query(String.format(
                "SELECT %s(\"consumption_kwh\") FROM \"%s\" WHERE cups = '%s'",
                selector,
                DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                supply.getCode()));

        // Times are requested in milliseconds so they come back as a number rather than as a
        // formatted string whose precision varies with the server configuration.
        QueryResult.Series series = firstSeries(connection.query(query, TimeUnit.MILLISECONDS));
        if (series == null) {
            return null;
        }
        List<Object> row = series.getValues().get(0);
        int index = series.getColumns().indexOf(COLUMN_TIME);
        if (index < 0 || index >= row.size() || !(row.get(index) instanceof Number time)) {
            return null;
        }
        return dateConverter.convertMillisecondsToInstant(time.longValue());
    }

    /**
     * The single row an aggregate or selector query returns, or null when the query matched no
     * record at all.
     */
    private QueryResult.Series firstSeries(QueryResult queryResult) {
        if (queryResult == null || queryResult.getResults() == null) {
            return null;
        }
        return queryResult.getResults().stream()
                .filter(result -> result.getSeries() != null)
                .flatMap(result -> result.getSeries().stream())
                .filter(series -> series.getValues() != null && !series.getValues().isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * Reads a numeric column, treating an absent column and a null value alike as zero. A SUM over
     * a field none of the supply's records carry is null, not zero.
     */
    private double readDouble(QueryResult.Series series, List<Object> row, String column) {
        int index = series.getColumns().indexOf(column);
        if (index < 0 || index >= row.size()) {
            return 0d;
        }
        Object value = row.get(index);
        return value instanceof Number number ? number.doubleValue() : 0d;
    }
}
