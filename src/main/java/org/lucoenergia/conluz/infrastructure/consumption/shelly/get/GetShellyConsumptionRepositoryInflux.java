package org.lucoenergia.conluz.infrastructure.consumption.shelly.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyMqttPowerMessagePoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Qualifier("getShellyConsumptionRepositoryInflux")
public class GetShellyConsumptionRepositoryInflux implements GetShellyConsumptionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetShellyConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<ShellyInstantConsumption> getHourlyConsumptionsByRangeOfDatesAndSupply(Supply supply, OffsetDateTime startDate,
                                                                                       OffsetDateTime endDate) {
        String startDateAsString = dateConverter.convertToString(startDate);
        String endDateAsString = dateConverter.convertToString(endDate);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT time, %s, %s FROM \"%s\" WHERE prefix = '%s' AND time >= '%s' AND time <= '%s'",
                    ShellyInstantConsumptionPoint.PREFIX,
                    ShellyInstantConsumptionPoint.CONSUMPTION_KW,
                    ShellyInstantConsumptionPoint.MEASUREMENT,
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
                    ShellyInstantConsumptionPoint.CONSUMPTION_KW + " FROM " + ShellyInstantConsumptionPoint.MEASUREMENT;
            QueryResult queryResult = connection.query(new Query(query));

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ShellyInstantConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, ShellyInstantConsumptionPoint.class);
            return mapToInstantConsumption(consumptionPoints);
        }
    }

    @Override
    public List<ShellyConsumption> getAllConsumptions() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            String query = "SELECT time, " + ShellyConsumptionPoint.PREFIX + ", " +
                    ShellyConsumptionPoint.CONSUMPTION_KWH + " FROM " + ShellyConsumptionPoint.MEASUREMENT;
            QueryResult queryResult = connection.query(new Query(query));

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ShellyConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, ShellyConsumptionPoint.class);
            return mapToConsumption(consumptionPoints);
        }
    }

    @Override
    public List<ShellyInstantConsumption> getShellyMqttPowerMessagesByRangeOfDates(OffsetDateTime periodBefore, OffsetDateTime now) {
        String startDateAsString = dateConverter.convertToString(periodBefore);
        String endDateAsString = dateConverter.convertToString(now);

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Get Shelly MQTT power messages
            String query = String.format("SELECT time, %s, %s FROM \"%s\" WHERE time >= '%s' AND time <= '%s'",
                    ShellyMqttPowerMessagePoint.TOPIC, ShellyMqttPowerMessagePoint.VALUE,
                    ShellyMqttPowerMessagePoint.MEASUREMENT, startDateAsString, endDateAsString);
            QueryResult queryResult = connection.query(new Query(query));

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<ShellyMqttPowerMessagePoint> consumptionPoints =  resultMapper.toPOJO(queryResult, ShellyMqttPowerMessagePoint.class);

            // Parse results into instant consumptions
            return mapShellyMqttPowerMessagesToInstantConsumption(consumptionPoints);
        }
    }

    private List<ShellyInstantConsumption> mapToInstantConsumption(List<ShellyInstantConsumptionPoint> consumptionPoints) {
        return consumptionPoints.stream()
                .map(consumptionPoint -> new ShellyInstantConsumption.Builder()
                        .withPrefix(consumptionPoint.getPrefix())
                        .withChannel(consumptionPoint.getChannel())
                        .withConsumptionKW(consumptionPoint.getConsumptionKW())
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

    private List<ShellyInstantConsumption> mapShellyMqttPowerMessagesToInstantConsumption(List<ShellyMqttPowerMessagePoint> consumptionPoints) {

        List<ShellyInstantConsumption> parsedConsumptions = new ArrayList<>();

        for (ShellyMqttPowerMessagePoint messagePoint : consumptionPoints) {
            Optional<ShellyInstantConsumption> parsedConsumption = processMessage(messagePoint);
            parsedConsumption.ifPresent(parsedConsumptions::add);
        }

        return parsedConsumptions;
    }

    private Optional<ShellyInstantConsumption> processMessage(ShellyMqttPowerMessagePoint message) {
        if (getConsumptionInKw(message) > 0) {
            return Optional.of(new ShellyInstantConsumption.Builder()
                    .withConsumptionKW(getConsumptionInKw(message))
                    .withTimestamp(message.getTime())
                    .withPrefix(getPrefix(message))
                    .withChannel(getChannel(message))
                    .build());
        }
        return Optional.empty();
    }

    private Double getConsumptionInKw(ShellyMqttPowerMessagePoint message) {
        return convertFromWToKW(message.getValue());
    }

    private Double convertFromWToKW(Double energyInW) {
        return energyInW / 1000;
    }

    private String getPrefix(ShellyMqttPowerMessagePoint message) {
        String topic = message.getTopic();
        String[] slices = topic.split("/");
        return slices[1] + "/" + slices[2];
    }

    private String getChannel(ShellyMqttPowerMessagePoint message) {
        String topic = message.getTopic();
        String[] slices = topic.split("/");
        return slices[4];
    }
}