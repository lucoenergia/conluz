package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.production.HuaweiHourlyProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class HuaweiProductionMonthlyAggregationRepositoryInflux implements HuaweiProductionMonthlyAggregationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionMonthlyAggregationRepositoryInflux.class);

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public HuaweiProductionMonthlyAggregationRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                              DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public void aggregateMonthlyProduction(Plant plant, Month month, int year) {

        LOGGER.info("Aggregating monthly production for plant: {}, month: {}, year: {}",
                plant.getCode(), month, year);

        final String startDate = dateConverter.convertToFirstDayOfTheMonthAsString(month, year);
        final String endDate = dateConverter.convertToLastDayOfTheMonthAsString(month, year);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    """
                    SELECT
                        SUM("inverter_power") AS "inverter_power",
                        SUM("ongrid_power") AS "ongrid_power",
                        SUM("power_profit") AS "power_profit",
                        SUM("theory_power") AS "theory_power",
                        SUM("radiation_intensity") AS "radiation_intensity"
                    FROM "%s"
                    WHERE station_code = '%s'
                        AND time >= '%s'
                        AND time <= '%s'
                    GROUP BY station_code
                    """,
                    HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                    plant.getCode(),
                    startDate,
                    endDate));

            QueryResult queryResult = connection.query(query);

            if (queryResult.hasError()) {
                LOGGER.error("Query to aggregate monthly production returned error: {}", queryResult.getError());
                return;
            }

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<HuaweiHourlyProductionPoint> aggregatedData = resultMapper.toPOJO(queryResult, HuaweiHourlyProductionPoint.class);

            if (aggregatedData.isEmpty()) {
                LOGGER.warn("No hourly data found to aggregate for plant: {}, month: {}, year: {}",
                        plant.getCode(), month, year);
                return;
            }

            HuaweiHourlyProductionPoint aggregated = aggregatedData.get(0);

            LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
            String formattedDate = firstDayOfMonth.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            long timestamp = dateConverter.convertStringDateToMilliseconds(formattedDate + "T00:00");

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            Point point = Point.measurement(HuaweiConfig.HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT)
                    .time(timestamp, TimeUnit.MILLISECONDS)
                    .tag("station_code", plant.getCode())
                    .addField("inverter_power", aggregated.getInverterPower() != null ? aggregated.getInverterPower() : 0.0)
                    .addField("ongrid_power", aggregated.getOngridPower() != null ? aggregated.getOngridPower() : 0.0)
                    .addField("power_profit", aggregated.getPowerProfit() != null ? aggregated.getPowerProfit() : 0.0)
                    .addField("theory_power", aggregated.getTheoryPower() != null ? aggregated.getTheoryPower() : 0.0)
                    .addField("radiation_intensity", aggregated.getRadiationIntensity() != null ? aggregated.getRadiationIntensity() : 0.0)
                    .build();

            batchPoints.point(point);
            connection.write(batchPoints);

            LOGGER.info("Successfully aggregated monthly production for plant: {}, month: {}, year: {}",
                    plant.getCode(), month, year);
        }
    }
}
