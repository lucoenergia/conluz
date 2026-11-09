package org.lucoenergia.conluz.infrastructure.datadis.metrics;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private static final String COLUMN_PUBLISHED_HOURS = "published_hours";
    private static final String TAG_CUPS = "cups";

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

            // Half-open, [startDate, endDate): a record on the end bound belongs to the next period.
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
                                AND time < '%s'
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
    public double sumSelfConsumptionKWh(Supply supply, Instant from, Instant to) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // >= and <, as in the sibling aggregate: the upper bound is exclusive so that adjacent
            // sub-intervals of one period do not both count the boundary record.
            // convertToString formats nine fractional digits, so a bound that differs from another
            // by a single nanosecond still reaches InfluxDB as a distinct instant.
            Query query = new Query(String.format(
                    """
                            SELECT SUM("self_consumption_energy_kwh") AS "self_consumption_energy_kwh"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time < '%s'
                            """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(to)));

            QueryResult.Series series = firstSeries(connection.query(query));
            if (series == null) {
                return 0d;
            }
            return readDouble(series, series.getValues().get(0), COLUMN_SELF_CONSUMPTION_ENERGY_KWH);
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

    @Override
    public Optional<RecordedConsumptionPeriod> findPublishedPeriod(Supply supply, Instant from,
                                                                   Instant toExclusive) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // As in findRecordedPeriod, FIRST and LAST are queried separately so each reports the
            // timestamp of its own record.
            Instant firstRecord = publishedSelectorTime(connection, "FIRST", supply, from, toExclusive);
            Instant lastRecord = publishedSelectorTime(connection, "LAST", supply, from, toExclusive);

            if (firstRecord == null || lastRecord == null) {
                return Optional.empty();
            }
            return Optional.of(new RecordedConsumptionPeriod(firstRecord, lastRecord));
        }
    }

    @Override
    public long countPublishedHours(Supply supply, Instant from, Instant toExclusive) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // COUNT over the field itself counts the records carrying it, zeros included, and
            // skips those where it is absent.
            Query query = new Query(String.format(
                    """
                            SELECT COUNT("self_consumption_energy_kwh") AS "published_hours"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time < '%s'
                            """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(toExclusive)));

            QueryResult.Series series = firstSeries(connection.query(query));
            if (series == null) {
                return 0L;
            }
            return (long) readDouble(series, series.getValues().get(0), COLUMN_PUBLISHED_HOURS);
        }
    }

    @Override
    public List<DailyPublishedAggregate> aggregateByLocalDay(Supply supply, Instant from, Instant toExclusive,
                                                             ZoneId zone) {
        return aggregateByLocalDay(List.of(supply), from, toExclusive, zone)
                .getOrDefault(supply.getCode(), List.of());
    }

    @Override
    public Map<String, List<DailyPublishedAggregate>> aggregateByLocalDay(Collection<Supply> supplies, Instant from,
                                                                          Instant toExclusive, ZoneId zone) {
        if (supplies.isEmpty()) {
            return Map.of();
        }
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // tz() aligns the day groups on local midnight, so a day with a daylight saving change
            // holds its 23 or 25 hours; fill(none) leaves out the days without any record. A group
            // starting before the interval only totals the records inside it, as the WHERE bounds
            // them. Grouping by cups as well returns one series per supply. tz() comes after fill(),
            // as InfluxQL requires.
            Query query = new Query(String.format(
                    """
                            SELECT
                                SUM("self_consumption_energy_kwh") AS "self_consumption_energy_kwh",
                                SUM("surplus_energy_kwh") AS "surplus_energy_kwh",
                                COUNT("self_consumption_energy_kwh") AS "published_hours"
                            FROM "%s"
                            WHERE (%s)
                                AND time >= '%s'
                                AND time < '%s'
                            GROUP BY time(1d), cups fill(none) tz('%s')
                            """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supplies.stream()
                            .map(supply -> String.format("cups = '%s'", supply.getCode()))
                            .collect(Collectors.joining(" OR ")),
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(toExclusive),
                    zone.getId()));

            // Times in milliseconds come back as a number, whatever the server's precision.
            QueryResult queryResult = connection.query(query, TimeUnit.MILLISECONDS);
            Map<String, List<DailyPublishedAggregate>> daysBySupplyCode = new HashMap<>();
            if (queryResult == null || queryResult.getResults() == null) {
                return daysBySupplyCode;
            }
            queryResult.getResults().stream()
                    .filter(result -> result.getSeries() != null)
                    .flatMap(result -> result.getSeries().stream())
                    .filter(series -> series.getTags() != null && series.getValues() != null)
                    .forEach(series -> daysBySupplyCode.put(series.getTags().get(TAG_CUPS),
                            toDays(series, zone)));
            return daysBySupplyCode;
        }
    }

    @Override
    public Map<Instant, Double> sumSurplusByHour(Collection<Supply> supplies, Instant from, Instant toExclusive,
                                                 ZoneId zone) {
        if (supplies.isEmpty()) {
            return Map.of();
        }
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Summed across the supplies in each hour, not grouped by cups: the caller needs the hour's
            // total, never a single supply's figure. Only records with surplus are read, since an hour
            // without any adds nothing to a sum, and fill(none) leaves out the hours they leave empty.
            // tz() aligns the hours as the community's other queries do.
            Query query = new Query(String.format(
                    """
                            SELECT SUM("surplus_energy_kwh") AS "surplus_energy_kwh"
                            FROM "%s"
                            WHERE (%s)
                                AND "surplus_energy_kwh" > 0
                                AND time >= '%s'
                                AND time < '%s'
                            GROUP BY time(1h) fill(none) tz('%s')
                            """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supplies.stream()
                            .map(supply -> String.format("cups = '%s'", supply.getCode()))
                            .collect(Collectors.joining(" OR ")),
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(toExclusive),
                    zone.getId()));

            // Times in milliseconds come back as a number, whatever the server's precision.
            QueryResult queryResult = connection.query(query, TimeUnit.MILLISECONDS);
            Map<Instant, Double> surplusByHour = new HashMap<>();
            if (queryResult == null || queryResult.getResults() == null) {
                return surplusByHour;
            }
            queryResult.getResults().stream()
                    .filter(result -> result.getSeries() != null)
                    .flatMap(result -> result.getSeries().stream())
                    .filter(series -> series.getValues() != null)
                    .forEach(series -> {
                        int timeIndex = series.getColumns().indexOf(COLUMN_TIME);
                        int surplusIndex = series.getColumns().indexOf(COLUMN_SURPLUS_ENERGY_KWH);
                        for (List<Object> row : series.getValues()) {
                            // An hour whose records all lack the field sums to null: no surplus was stored.
                            if (surplusIndex >= 0 && surplusIndex < row.size()
                                    && row.get(surplusIndex) instanceof Number surplus) {
                                surplusByHour.put(dateConverter.convertMillisecondsToInstant(
                                        ((Number) row.get(timeIndex)).longValue()), surplus.doubleValue());
                            }
                        }
                    });
            return surplusByHour;
        }
    }

    private List<DailyPublishedAggregate> toDays(QueryResult.Series series, ZoneId zone) {
        int timeIndex = series.getColumns().indexOf(COLUMN_TIME);
        return series.getValues().stream()
                .map(row -> new DailyPublishedAggregate(
                        dateConverter.convertMillisecondsToInstant(((Number) row.get(timeIndex)).longValue())
                                .atZone(zone).toLocalDate(),
                        readDouble(series, row, COLUMN_SELF_CONSUMPTION_ENERGY_KWH),
                        readDouble(series, row, COLUMN_SURPLUS_ENERGY_KWH),
                        (long) readDouble(series, row, COLUMN_PUBLISHED_HOURS)))
                .toList();
    }

    /**
     * The timestamp of the record a selector over {@code self_consumption_energy_kwh} picks in the
     * half-open interval, or null when no record there carries the field.
     */
    private Instant publishedSelectorTime(InfluxDB connection, String selector, Supply supply, Instant from,
                                          Instant toExclusive) {
        Query query = new Query(String.format(
                """
                        SELECT %s("self_consumption_energy_kwh")
                        FROM "%s"
                        WHERE cups = '%s'
                            AND time >= '%s'
                            AND time < '%s'
                        """,
                selector,
                DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                supply.getCode(),
                dateConverter.convertToString(from),
                dateConverter.convertToString(toExclusive)));
        return recordTime(connection, query);
    }

    private Instant selectorTime(InfluxDB connection, String selector, Supply supply) {
        Query query = new Query(String.format(
                "SELECT %s(\"consumption_kwh\") FROM \"%s\" WHERE cups = '%s'",
                selector,
                DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                supply.getCode()));
        return recordTime(connection, query);
    }

    /**
     * The timestamp of the single record a selector query returns, or null when the query matched
     * no record at all.
     */
    private Instant recordTime(InfluxDB connection, Query query) {
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
