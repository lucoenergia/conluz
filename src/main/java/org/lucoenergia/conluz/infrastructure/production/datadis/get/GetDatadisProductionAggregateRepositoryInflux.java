package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.RecordedProductionPeriod;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMeasurements;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sums the hourly production of plant supplies inside InfluxDB, one query for every code. Summed per code,
 * it is grouped by {@code cups} so each code comes back as its own series, and a code with no record in the
 * interval yields no series, and so no entry. Summed per year, it is grouped by local day, since InfluxQL
 * has no calendar-year interval, and the days are folded into years here.
 *
 * <p>The single-code reads count the records next to their sum: {@code COUNT} over the field counts every
 * stored record, zeros included, which is what tells a stored zero from an hour nothing was stored for.
 */
@Repository
public class GetDatadisProductionAggregateRepositoryInflux implements GetDatadisProductionAggregateRepository {

    private static final String COLUMN_PRODUCTION_KWH = "production_kwh";
    private static final String COLUMN_HOURS_WITH_DATA = "hours_with_data";
    private static final String COLUMN_TIME = "time";
    private static final String TAG_CUPS = "cups";

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetDatadisProductionAggregateRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                         DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public Map<String, Double> sumProductionByCups(Collection<String> cups, Instant from, Instant toExclusive) {
        if (cups.isEmpty()) {
            return Map.of();
        }
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Half-open, [from, toExclusive): a record on the end bound belongs to the next period.
            Query query = new Query(String.format(
                    """
                            SELECT SUM("production_kwh") AS "production_kwh"
                            FROM "%s"
                            WHERE (%s)
                                AND time >= '%s'
                                AND time < '%s'
                            GROUP BY cups
                            """,
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                    cups.stream()
                            .map(code -> String.format("cups = '%s'", code))
                            .collect(Collectors.joining(" OR ")),
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(toExclusive)));

            return readSums(connection.query(query));
        }
    }

    @Override
    public Map<Integer, Double> sumProductionByYear(Collection<String> cups, Instant from, Instant toExclusive,
                                                    ZoneId zone) {
        if (cups.isEmpty()) {
            return Map.of();
        }
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Summed across the codes in each local day: tz() starts every day at local midnight, so no day
            // straddles 1 January and each one belongs to a single local year. fill(none) leaves out the days
            // without records. The bounds and the codes are those of sumProductionByCups.
            Query query = new Query(String.format(
                    """
                            SELECT SUM("production_kwh") AS "production_kwh"
                            FROM "%s"
                            WHERE (%s)
                                AND time >= '%s'
                                AND time < '%s'
                            GROUP BY time(1d) fill(none) tz('%s')
                            """,
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                    cups.stream()
                            .map(code -> String.format("cups = '%s'", code))
                            .collect(Collectors.joining(" OR ")),
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(toExclusive),
                    zone.getId()));

            // Times in milliseconds come back as a number, whatever the server's precision.
            return readYearlySums(connection.query(query, TimeUnit.MILLISECONDS), zone);
        }
    }

    @Override
    public Optional<ProductionAggregate> aggregate(String cups, Instant from, Instant toExclusive) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Half-open, [from, toExclusive), as sumProductionByCups.
            Query query = new Query(String.format(
                    """
                            SELECT SUM("production_kwh") AS "production_kwh",
                                   COUNT("production_kwh") AS "hours_with_data"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time < '%s'
                            """,
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                    cups,
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(toExclusive)));

            // An interval without records yields no series at all.
            return firstSeries(connection.query(query))
                    .flatMap(series -> readAggregate(series.getValues().get(0), series.getColumns()));
        }
    }

    @Override
    public List<DailyProductionAggregate> aggregateByLocalDay(String cups, Instant from, Instant toExclusive,
                                                              ZoneId zone) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // tz() starts every day at local midnight, so a daylight saving day is grouped as its 23 or 25
            // hours. fill(none) leaves out the days without records. The bounds are those of aggregate.
            Query query = new Query(String.format(
                    """
                            SELECT SUM("production_kwh") AS "production_kwh",
                                   COUNT("production_kwh") AS "hours_with_data"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time < '%s'
                            GROUP BY time(1d) fill(none) tz('%s')
                            """,
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                    cups,
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(toExclusive),
                    zone.getId()));

            // Times in milliseconds come back as a number, whatever the server's precision.
            return firstSeries(connection.query(query, TimeUnit.MILLISECONDS))
                    .map(series -> toDays(series, zone))
                    .orElse(List.of());
        }
    }

    @Override
    public Optional<RecordedProductionPeriod> findRecordedPeriod(String cups) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // FIRST and LAST go in two statements of one request: a row carrying two selectors cannot also
            // carry their two different timestamps, and InfluxDB reports the epoch instead.
            Query query = new Query(String.format(
                    """
                            SELECT FIRST("production_kwh") FROM "%1$s" WHERE cups = '%2$s';
                            SELECT LAST("production_kwh") FROM "%1$s" WHERE cups = '%2$s'
                            """,
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                    cups));

            QueryResult queryResult = connection.query(query, TimeUnit.MILLISECONDS);
            if (queryResult == null || queryResult.getResults() == null || queryResult.getResults().size() != 2) {
                return Optional.empty();
            }
            Optional<Instant> firstRecord = recordTime(queryResult.getResults().get(0));
            Optional<Instant> lastRecord = recordTime(queryResult.getResults().get(1));
            if (firstRecord.isEmpty() || lastRecord.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new RecordedProductionPeriod(firstRecord.get(), lastRecord.get()));
        }
    }

    private Optional<Instant> recordTime(QueryResult.Result result) {
        if (result.getSeries() == null || result.getSeries().isEmpty()) {
            return Optional.empty();
        }
        QueryResult.Series series = result.getSeries().get(0);
        if (series.getValues() == null || series.getValues().isEmpty()) {
            return Optional.empty();
        }
        List<Object> row = series.getValues().get(0);
        int index = series.getColumns().indexOf(COLUMN_TIME);
        if (index < 0 || index >= row.size() || !(row.get(index) instanceof Number time)) {
            return Optional.empty();
        }
        return Optional.of(dateConverter.convertMillisecondsToInstant(time.longValue()));
    }

    private List<DailyProductionAggregate> toDays(QueryResult.Series series, ZoneId zone) {
        int timeIndex = series.getColumns().indexOf(COLUMN_TIME);
        return series.getValues().stream()
                .flatMap(row -> readAggregate(row, series.getColumns())
                        .map(aggregate -> new DailyProductionAggregate(
                                dateConverter.convertMillisecondsToInstant(((Number) row.get(timeIndex)).longValue())
                                        .atZone(zone).toLocalDate(),
                                aggregate.productionKWh(),
                                aggregate.hoursWithData()))
                        .stream())
                .toList();
    }

    /**
     * The row's sum and count, or empty when it counts no record.
     */
    private static Optional<ProductionAggregate> readAggregate(List<Object> row, List<String> columns) {
        int productionIndex = columns.indexOf(COLUMN_PRODUCTION_KWH);
        int countIndex = columns.indexOf(COLUMN_HOURS_WITH_DATA);
        if (!(row.get(countIndex) instanceof Number count) || count.longValue() == 0
                || !(row.get(productionIndex) instanceof Number production)) {
            return Optional.empty();
        }
        return Optional.of(new ProductionAggregate(production.doubleValue(), count.longValue()));
    }

    private static Optional<QueryResult.Series> firstSeries(QueryResult queryResult) {
        if (queryResult == null || queryResult.getResults() == null) {
            return Optional.empty();
        }
        return queryResult.getResults().stream()
                .filter(result -> result.getSeries() != null)
                .flatMap(result -> result.getSeries().stream())
                .filter(series -> series.getValues() != null && !series.getValues().isEmpty())
                .findFirst();
    }

    private Map<Integer, Double> readYearlySums(QueryResult queryResult, ZoneId zone) {
        Map<Integer, Double> sums = new HashMap<>();
        if (queryResult == null || queryResult.getResults() == null) {
            return sums;
        }
        queryResult.getResults().stream()
                .filter(result -> result.getSeries() != null)
                .flatMap(result -> result.getSeries().stream())
                .filter(series -> series.getValues() != null)
                .forEach(series -> {
                    int timeIndex = series.getColumns().indexOf(COLUMN_TIME);
                    int productionIndex = series.getColumns().indexOf(COLUMN_PRODUCTION_KWH);
                    for (List<Object> row : series.getValues()) {
                        // A day whose records all lack the field sums to null: nothing was stored.
                        if (productionIndex >= 0 && productionIndex < row.size()
                                && row.get(productionIndex) instanceof Number production) {
                            int year = dateConverter.convertMillisecondsToInstant(
                                    ((Number) row.get(timeIndex)).longValue()).atZone(zone).getYear();
                            sums.merge(year, production.doubleValue(), Double::sum);
                        }
                    }
                });
        return sums;
    }

    private Map<String, Double> readSums(QueryResult queryResult) {
        Map<String, Double> sums = new HashMap<>();
        if (queryResult == null || queryResult.getResults() == null) {
            return sums;
        }
        queryResult.getResults().stream()
                .filter(result -> result.getSeries() != null)
                .flatMap(result -> result.getSeries().stream())
                .filter(series -> series.getTags() != null && series.getValues() != null
                        && !series.getValues().isEmpty())
                .forEach(series -> {
                    List<Object> row = series.getValues().get(0);
                    int index = series.getColumns().indexOf(COLUMN_PRODUCTION_KWH);
                    // A series whose records all lack the field sums to null: nothing was stored.
                    if (index >= 0 && index < row.size() && row.get(index) instanceof Number number) {
                        sums.put(series.getTags().get(TAG_CUPS), number.doubleValue());
                    }
                });
        return sums;
    }
}
