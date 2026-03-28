package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionMonthlyPoint;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class DatadisYearlyAggregationRepositoryInflux implements DatadisYearlyAggregationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisYearlyAggregationRepositoryInflux.class);

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public DatadisYearlyAggregationRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                    DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public void aggregateYearlyConsumption(Supply supply, int year) {

        LOGGER.info("Aggregating yearly consumption for supply ID: {}, year: {}", supply.getId(), year);

        String startDate = String.format("%d-01-01T00:00:00Z", year);
        String endDate = String.format("%d-12-31T23:59:59Z", year);

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
                        AND time <= '%s'
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

            // Calculate timestamp for January 1st of the year at midnight (local timezone)
            LocalDate firstDayOfYear = LocalDate.of(year, 1, 1);
            String formattedDate = firstDayOfYear.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            long timestamp = dateConverter.convertStringDateToMilliseconds(formattedDate + "T00:00");

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            Point point = Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT)
                    .time(timestamp, TimeUnit.MILLISECONDS)
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
