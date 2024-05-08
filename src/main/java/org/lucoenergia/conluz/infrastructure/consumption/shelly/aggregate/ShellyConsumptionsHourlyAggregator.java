package org.lucoenergia.conluz.infrastructure.consumption.shelly.aggregate;

import org.apache.commons.lang3.StringUtils;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyAggregateConsumptionPoint;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.PersistShellyConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ShellyConsumptionsHourlyAggregator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyConsumptionsHourlyAggregator.class);

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;
    private final GetSupplyRepository getSupplyRepository;
    private final PersistShellyConsumptionRepository persistShellyConsumptionRepository;

    public ShellyConsumptionsHourlyAggregator(InfluxDbConnectionManager influxDbConnectionManager,
                                              DateConverter dateConverter,
                                              GetSupplyRepository getSupplyRepository,
                                              PersistShellyConsumptionRepository persistShellyConsumptionRepository) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
        this.getSupplyRepository = getSupplyRepository;
        this.persistShellyConsumptionRepository = persistShellyConsumptionRepository;
    }

    public void aggregate(OffsetDateTime startDate, OffsetDateTime endDate) {

        String startDateAsString = dateConverter.convertToString(startDate);
        String endDateAsString = dateConverter.convertToString(endDate);

        LOGGER.info("Aggregating Shelly consumption from {} to {}", startDateAsString, endDateAsString);

        List<Supply> supplies = getSupplyRepository.findAll();

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            for (Supply supply : supplies) {

                if (StringUtils.isBlank(supply.getShellyId())) {
                    continue;
                }

                LOGGER.info("Aggregating Shelly consumption from supply {}", supply.getShellyId());

                try {
                    Query query = new Query(String.format(
                            "SELECT INTEGRAL(%s, 1h) AS %s FROM \"%s\" WHERE %s = '%s' AND time >= '%s' AND time <= '%s' GROUP BY time(1h)",
                            ShellyInstantConsumptionPoint.CONSUMPTION_KW,
                            ShellyInstantConsumptionPoint.CONSUMPTION_KW,
                            ShellyInstantConsumptionPoint.MEASUREMENT,
                            ShellyInstantConsumptionPoint.PREFIX,
                            supply.getShellyMqttPrefix(),
                            startDateAsString,
                            endDateAsString));

                    QueryResult queryResult = connection.query(query);
                    InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
                    List<ShellyAggregateConsumptionPoint> consumptionPoints = resultMapper.toPOJO(queryResult, ShellyAggregateConsumptionPoint.class);

                    persistShellyConsumptionRepository.persistConsumptions(mapToConsumption(consumptionPoints, supply));
                } catch (Exception e) {
                    LOGGER.error(String.format("Unable to aggregate Shelly instant consumptions for supply with ID %s", supply.getId()), e);
                }
            }
        }
    }

    private List<ShellyConsumption> mapToConsumption(List<ShellyAggregateConsumptionPoint> consumptionPoints, Supply supply) {
        return consumptionPoints.stream()
                .map(consumptionPoint -> new ShellyConsumption.Builder()
                        .withPrefix(supply.getShellyMqttPrefix())
                        .withConsumptionKWh(consumptionPoint.getConsumptionKW())
                        .withTimestamp(consumptionPoint.getTime())
                        .build())
                .toList();
    }
}
