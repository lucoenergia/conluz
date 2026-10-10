package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMeasurements;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class DatadisProductionMonthlyAggregationRepositoryInflux implements DatadisProductionMonthlyAggregationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisProductionMonthlyAggregationRepositoryInflux.class);

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;
    private final ZoneResolver zoneResolver;

    public DatadisProductionMonthlyAggregationRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                               DateConverter dateConverter,
                                                               ZoneResolver zoneResolver) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
        this.zoneResolver = zoneResolver;
    }

    /**
     * Sums the hourly measurement over the month as the supply's calendar sees it:
     * {@code [local midnight of the 1st, local midnight of the 1st of the next month)}. Literal UTC
     * bounds used to drop the month's first local hour or two and pick up the first local hour or
     * two of the next month instead.
     *
     * <p>The point is stamped at the start of that same window, which is where it has always been
     * stamped, and the tag set stays {@code cups} alone, so re-running this overwrites the existing
     * point rather than adding one.
     */
    @Override
    public void aggregateMonthlyProduction(Supply supply, Month month, int year) {

        final ZoneId zoneId = zoneResolver.resolveZoneIdForSupply(supply.getId());
        final LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
        final Instant startOfMonth = firstDayOfMonth.atStartOfDay(zoneId).toInstant();
        final Instant startOfNextMonth = firstDayOfMonth.plusMonths(1).atStartOfDay(zoneId).toInstant();
        final String startDate = dateConverter.convertToString(startOfMonth);
        final String endDate = dateConverter.convertToString(startOfNextMonth);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Query to aggregate hourly data into monthly totals
            Query query = new Query(String.format(
                    """
                    SELECT
                        SUM("production_kwh") AS "production_kwh",
                        LAST("obtain_method") AS "obtain_method"
                    FROM "%s"
                    WHERE cups = '%s'
                        AND time >= '%s'
                        AND time < '%s'
                    GROUP BY cups
                    """,
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    startDate,
                    endDate));

            QueryResult queryResult = connection.query(query);

            if (queryResult.hasError()) {
                LOGGER.error("Query to aggregate monthly production returned error: {}", queryResult.getError());
                return;
            }

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisProductionPoint> aggregatedData = resultMapper.toPOJO(queryResult, DatadisProductionPoint.class);

            if (aggregatedData.isEmpty()) {
                LOGGER.warn("No hourly data found to aggregate for supply: {}, month: {}, year: {}",
                        supply.getCode(), month, year);
                return;
            }

            // Persist the aggregated monthly data
            DatadisProductionPoint aggregated = aggregatedData.get(0);

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            Point point = Point.measurement(DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT)
                    .time(startOfMonth.toEpochMilli(), TimeUnit.MILLISECONDS)
                    .tag("cups", supply.getCode())
                    .addField("production_kwh", aggregated.getProductionKWh() != null ? aggregated.getProductionKWh() : 0.0)
                    .addField("obtain_method", aggregated.getObtainMethod() != null ? aggregated.getObtainMethod() : "")
                    .build();

            batchPoints.point(point);
            connection.write(batchPoints);

            LOGGER.debug("Persisted monthly aggregation for supply: {}, month: {}, year: {}",
                    supply.getCode(), month, year);
        }
    }
}
