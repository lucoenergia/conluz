package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDuration;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Month;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@Qualifier("getDatadisConsumptionRepositoryInflux")
public class GetDatadisConsumptionRepositoryInflux implements GetDatadisConsumptionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetDatadisConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                 DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionsByMonth(Supply supply, Month month, int year) {

        String startDate = dateConverter.convertToFirstDayOfTheMonthAsString(month, year);
        String endDate = dateConverter.convertToLastDayOfTheMonthAsString(month, year);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    startDate,
                    endDate));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, DatadisConsumptionPoint.class);
            return mapToConsumption(consumptionPoints);
        }
    }

    @Override
    public List<DatadisConsumption> getDailyConsumptionsByRangeOfDates(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    """
                            SELECT
                                SUM("consumption_kwh") AS "consumption_kwh",
                                SUM("surplus_energy_kwh") AS "surplus_energy_kwh",
                                SUM("self_consumption_energy_kwh") AS "self_consumption_energy_kwh",
                                LAST("obtain_method") AS "obtain_method"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time <= '%s'
                            GROUP BY time(%s), cups
                            """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate),
                    InfluxDuration.DAILY));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, DatadisConsumptionPoint.class);
            return mapToConsumption(consumptionPoints);
        }
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionsByRangeOfDates(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    """
                            SELECT
                                SUM("consumption_kwh") AS "consumption_kwh",
                                SUM("surplus_energy_kwh") AS "surplus_energy_kwh",
                                SUM("self_consumption_energy_kwh") AS "self_consumption_energy_kwh",
                                LAST("obtain_method") AS "obtain_method"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time <= '%s'
                            GROUP BY time(%s), cups
                            """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate),
                    InfluxDuration.HOURLY));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<DatadisConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, DatadisConsumptionPoint.class);
            return mapToConsumption(consumptionPoints);
        }
    }

    private List<DatadisConsumption> mapToConsumption(List<DatadisConsumptionPoint> consumptionPoints) {
        // Map fields from datadisConsumptionPoint to consumption here
        return consumptionPoints.stream()
                .map(consumptionPoint -> {
                    DatadisConsumption consumption = new DatadisConsumption();
                    consumption.setCups(consumptionPoint.getCups());
                    consumption.setDate(dateConverter.convertFromInstantToStringDate(consumptionPoint.getTime()));
                    consumption.setTime(dateConverter.convertFromInstantToStringTime(consumptionPoint.getTime()));
                    consumption.setConsumptionKWh(parseToFloat(consumptionPoint.getConsumptionKWh()));
                    consumption.setSelfConsumptionEnergyKWh(parseToFloat(consumptionPoint.getSelfConsumptionEnergyKWh()));
                    consumption.setSurplusEnergyKWh(parseToFloat(consumptionPoint.getSurplusEnergyKWh()));
                    consumption.setObtainMethod(consumptionPoint.getObtainMethod());
                    return consumption;
                })
                .toList();
    }

    private Float parseToFloat(Double value) {
        if (value == null) {
            return 0.0f;
        }
        try {
            return value.floatValue();
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }
}