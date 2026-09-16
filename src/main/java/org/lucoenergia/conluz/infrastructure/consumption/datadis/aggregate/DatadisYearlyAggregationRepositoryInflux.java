package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionMonthlyPoint;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class DatadisYearlyAggregationRepositoryInflux implements DatadisYearlyAggregationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisYearlyAggregationRepositoryInflux.class);

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;
    private final ZoneResolver zoneResolver;

    public DatadisYearlyAggregationRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                    DateConverter dateConverter,
                                                    ZoneResolver zoneResolver) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
        this.zoneResolver = zoneResolver;
    }

    /**
     * Sums the monthly measurement over the year as the supply's calendar sees it:
     * {@code [local midnight of January 1st, local midnight of the next January 1st)}. Monthly points
     * are stamped at local midnight, so January's sat at 23:00Z or 22:00Z of the previous year and
     * the literal UTC window {@code >= yyyy-01-01T00:00:00Z} excluded it outright -- every stored
     * yearly total was the sum of February through December.
     *
     * <p>The point is stamped at the start of that same window, which is where it has always been
     * stamped, and the tag set stays {@code cups} alone, so re-running this overwrites the existing
     * point rather than adding one.
     */
    @Override
    public void aggregateYearlyConsumption(Supply supply, int year) {

        LOGGER.info("Aggregating yearly consumption for supply ID: {}, year: {}", supply.getId(), year);

        final ZoneId zoneId = zoneResolver.resolveZoneIdForSupply(supply.getId());
        final Instant startOfYear = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant();
        final Instant startOfNextYear = LocalDate.of(year + 1, 1, 1).atStartOfDay(zoneId).toInstant();
        final String startDate = dateConverter.convertToString(startOfYear);
        final String endDate = dateConverter.convertToString(startOfNextYear);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Query to aggregate monthly data into yearly totals
            Query query = new Query(String.format(
                    """
                    SELECT
                        SUM("consumption_kwh") AS "consumption_kwh",
                        SUM("surplus_energy_kwh") AS "surplus_energy_kwh",
                        SUM("self_consumption_energy_kwh") AS "self_consumption_energy_kwh",
                        SUM("generation_energy_kwh") AS "generation_energy_kwh",
                        LAST("obtain_method") AS "obtain_method"
                    FROM "%s"
                    WHERE cups = '%s'
                        AND time >= '%s'
                        AND time < '%s'
                    GROUP BY cups
                    """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT,
                    supply.getCode(),
                    startDate,
                    endDate));

            QueryResult queryResult = connection.query(query);

            if (queryResult.hasError()) {
                LOGGER.error("Query to aggregate yearly consumption returned error: {}", queryResult.getError());
                return;
            }

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisConsumptionMonthlyPoint> aggregatedData = resultMapper.toPOJO(queryResult, DatadisConsumptionMonthlyPoint.class);

            if (aggregatedData.isEmpty()) {
                LOGGER.warn("No monthly data found to aggregate for supply: {}, year: {}", supply.getCode(), year);
                return;
            }

            // Persist the aggregated yearly data
            DatadisConsumptionMonthlyPoint aggregated = aggregatedData.get(0);

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            Point point = Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT)
                    .time(startOfYear.toEpochMilli(), TimeUnit.MILLISECONDS)
                    .tag("cups", supply.getCode())
                    .addField("consumption_kwh", aggregated.getConsumptionKWh() != null ? aggregated.getConsumptionKWh() : 0.0)
                    .addField("surplus_energy_kwh", aggregated.getSurplusEnergyKWh() != null ? aggregated.getSurplusEnergyKWh() : 0.0)
                    .addField("generation_energy_kwh", aggregated.getGenerationEnergyKWh() != null ? aggregated.getGenerationEnergyKWh() : 0.0)
                    .addField("self_consumption_energy_kwh", aggregated.getSelfConsumptionEnergyKWh() != null ? aggregated.getSelfConsumptionEnergyKWh() : 0.0)
                    .addField("obtain_method", aggregated.getObtainMethod() != null ? aggregated.getObtainMethod() : "")
                    .build();

            batchPoints.point(point);
            connection.write(batchPoints);

            LOGGER.info("Successfully aggregated yearly consumption for supply ID: {}, year: {}", supply.getId(), year);
        }
    }
}
