package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sums the hourly production of plant supplies inside InfluxDB, one query for every code. Summed per code,
 * it is grouped by {@code cups} so each code comes back as its own series, and a code with no record in the
 * interval yields no series, and so no entry. Summed per year, it is grouped by local day, since InfluxQL
 * has no calendar-year interval, and the days are folded into years here.
 */
@Repository
public class GetDatadisProductionAggregateRepositoryInflux implements GetDatadisProductionAggregateRepository {

    private static final String COLUMN_PRODUCTION_KWH = "production_kwh";
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
