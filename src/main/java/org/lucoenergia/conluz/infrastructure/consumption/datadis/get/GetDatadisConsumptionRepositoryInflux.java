package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisDateTimeConverter;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Month;
import java.util.List;

@Repository
@Qualifier("getDatadisConsumptionRepositoryInflux")
public class GetDatadisConsumptionRepositoryInflux implements GetDatadisConsumptionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;
    private final DatadisDateTimeConverter datadisDateTimeConverter;

    public GetDatadisConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                 DateConverter dateConverter, DatadisDateTimeConverter datadisDateTimeConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
        this.datadisDateTimeConverter = datadisDateTimeConverter;
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

    private List<DatadisConsumption> mapToConsumption(List<DatadisConsumptionPoint> consumptionPoints) {
        // Map fields from datadisConsumptionPoint to consumption here
        return consumptionPoints.stream()
                .map(consumptionPoint -> {
                    DatadisConsumption consumption = new DatadisConsumption();
                    consumption.setCups(consumptionPoint.getCups());
                    consumption.setDate(datadisDateTimeConverter.convertFromInstantToDate(consumptionPoint.getTime()));
                    consumption.setTime(datadisDateTimeConverter.convertFromInstantToTime(consumptionPoint.getTime()));
                    consumption.setConsumptionKWh(consumptionPoint.getConsumptionKWh().floatValue());
                    consumption.setObtainMethod(consumptionPoint.getObtainMethod());
                    consumption.setSurplusEnergyKWh(consumptionPoint.getSurplusEnergyKWh().floatValue());
                    return consumption;
                })
                .toList();
    }
}