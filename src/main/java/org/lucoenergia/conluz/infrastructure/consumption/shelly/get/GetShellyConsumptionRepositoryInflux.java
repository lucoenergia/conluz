package org.lucoenergia.conluz.infrastructure.consumption.shelly.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConfig;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.DateToInfluxDbDateFormatConverter;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@Qualifier("getShellyConsumptionRepositoryInflux")
public class GetShellyConsumptionRepositoryInflux implements GetShellyConsumptionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateToInfluxDbDateFormatConverter dateConverter;

    public GetShellyConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                DateToInfluxDbDateFormatConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<ShellyInstantConsumption> getHourlyConsumptionsByRangeOfDatesAndSupply(Supply supply, OffsetDateTime startDate,
                                                                                       OffsetDateTime endDate) {
        String startDateAsString = dateConverter.convert(startDate);
        String endDateAsString = dateConverter.convert(endDate);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT time, %s, %s FROM \"%s\" WHERE prefix = '%s' AND time >= '%s' AND time <= '%s'",
                    ShellyInstantConsumptionPoint.PREFIX,
                    ShellyInstantConsumptionPoint.CONSUMPTION_KWH,
                    ShellyConfig.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getShellyMqttPrefix(),
                    startDateAsString,
                    endDateAsString));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ShellyInstantConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, ShellyInstantConsumptionPoint.class);
            return mapToInstantConsumption(consumptionPoints);
        }
    }

    @Override
    public List<ShellyInstantConsumption> getAllInstantConsumptions() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            String query = "SELECT time, " + ShellyInstantConsumptionPoint.PREFIX + ", " + ShellyInstantConsumptionPoint.CHANNEL + ", " +
                    ShellyInstantConsumptionPoint.CONSUMPTION_KWH + " FROM " + ShellyConfig.CONSUMPTION_KW_MEASUREMENT;
            QueryResult queryResult = connection.query(new Query(query));

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ShellyInstantConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, ShellyInstantConsumptionPoint.class);
            return mapToInstantConsumption(consumptionPoints);
        }
    }

    @Override
    public List<ShellyConsumption> getAllConsumptions() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            String query = "SELECT time, " + ShellyInstantConsumptionPoint.PREFIX + ", " +
                    ShellyInstantConsumptionPoint.CONSUMPTION_KWH + " FROM " + ShellyConfig.CONSUMPTION_KWH_MEASUREMENT;
            QueryResult queryResult = connection.query(new Query(query));

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ShellyConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, ShellyConsumptionPoint.class);
            return mapToConsumption(consumptionPoints);
        }
    }

    private List<ShellyInstantConsumption> mapToInstantConsumption(List<ShellyInstantConsumptionPoint> consumptionPoints) {
        return consumptionPoints.stream()
                .map(consumptionPoint -> new ShellyInstantConsumption.Builder()
                        .withPrefix(consumptionPoint.getPrefix())
                        .withChannel(consumptionPoint.getChannel())
                        .withConsumptionKWh(consumptionPoint.getConsumptionKWh())
                        .withTimestamp(consumptionPoint.getTime())
                        .build())
                .toList();
    }

    private List<ShellyConsumption> mapToConsumption(List<ShellyConsumptionPoint> consumptionPoints) {
        return consumptionPoints.stream()
                .map(consumptionPoint -> new ShellyConsumption.Builder()
                        .withPrefix(consumptionPoint.getPrefix())
                        .withConsumptionKWh(consumptionPoint.getConsumptionKWh())
                        .withTimestamp(consumptionPoint.getTime())
                        .build())
                .toList();
    }
}