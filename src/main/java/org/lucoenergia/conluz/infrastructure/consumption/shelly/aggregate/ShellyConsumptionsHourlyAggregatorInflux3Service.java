package org.lucoenergia.conluz.infrastructure.consumption.shelly.aggregate;

import com.influxdb.v3.client.InfluxDBClient;
import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.PersistShellyConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3Duration;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@Qualifier("shellyConsumptionsHourlyAggregatorInflux3Service")
public class ShellyConsumptionsHourlyAggregatorInflux3Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyConsumptionsHourlyAggregatorInflux3Service.class);

    private final InfluxDb3ConnectionManager connectionManager;
    private final DateConverter dateConverter;
    private final GetSupplyRepository getSupplyRepository;
    private final PersistShellyConsumptionRepository persistShellyConsumptionRepository;

    public ShellyConsumptionsHourlyAggregatorInflux3Service(InfluxDb3ConnectionManager connectionManager,
                                                            DateConverter dateConverter,
                                                            GetSupplyRepository getSupplyRepository,
                                                            @Qualifier("persistShellyConsumptionRepositoryInflux3")
                                                            PersistShellyConsumptionRepository persistShellyConsumptionRepository) {
        this.connectionManager = connectionManager;
        this.dateConverter = dateConverter;
        this.getSupplyRepository = getSupplyRepository;
        this.persistShellyConsumptionRepository = persistShellyConsumptionRepository;
    }

    public void aggregate(OffsetDateTime startDate, OffsetDateTime endDate) {

        String startDateAsString = dateConverter.convertToString(startDate);
        String endDateAsString = dateConverter.convertToString(endDate);

        LOGGER.info("Aggregating Shelly consumption from {} to {}", startDateAsString, endDateAsString);

        List<Supply> supplies = getSupplyRepository.findAll();
        InfluxDBClient client = connectionManager.getClient();

        for (Supply supply : supplies) {

            if (StringUtils.isBlank(supply.getShellyId())) {
                continue;
            }

            LOGGER.info("Aggregating Shelly consumption from supply {}", supply.getShellyId());

            try {
                // InfluxDB 3 SQL query to aggregate hourly consumption
                // We need to calculate the integral (area under the curve) for power consumption over time
                // In SQL, we can use AVG with time bucketing to approximate the integral
                String query = String.format(
                        """
                        SELECT
                            DATE_TRUNC('%s', time) as time,
                            AVG(consumption_kw) AS consumption_kw
                        FROM "%s"
                        WHERE prefix = '%s'
                            AND time >= '%s'
                            AND time <= '%s'
                        GROUP BY DATE_TRUNC('%s', time)
                        ORDER BY time
                        """,
                        InfluxDb3Duration.HOURLY,
                        ShellyInstantConsumptionPoint.MEASUREMENT,
                        supply.getShellyMqttPrefix(),
                        startDateAsString,
                        endDateAsString,
                        InfluxDb3Duration.HOURLY
                );

                List<ShellyConsumption> consumptions = executeAggregationQuery(client, query, supply);

                if (!consumptions.isEmpty()) {
                    LOGGER.debug("Aggregated {} Shelly consumption points from supply {}", consumptions.size(), supply.getShellyId());
                    persistShellyConsumptionRepository.persistConsumptions(consumptions);
                } else {
                    LOGGER.debug("Aggregated Shelly consumption results from supply {} are empty.", supply.getShellyId());
                }

            } catch (Exception e) {
                LOGGER.error("Unable to aggregate Shelly instant consumptions for supply with ID {}", supply.getId(), e);
            }
        }
    }

    private List<ShellyConsumption> executeAggregationQuery(InfluxDBClient client, String query, Supply supply) {
        List<ShellyConsumption> results = new ArrayList<>();

        try (Stream<Object[]> stream = client.query(query)) {
            stream.forEach(row -> {
                // Expected columns: time, consumption_kw
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    Instant time;
                    if (row[0] instanceof Instant) {
                        time = (Instant) row[0];
                    } else if (row[0] instanceof Number) {
                        long nanos = ((Number) row[0]).longValue();
                        time = Instant.ofEpochSecond(0, nanos);
                    } else {
                        throw new IllegalArgumentException("Unexpected timestamp type: " + row[0].getClass());
                    }

                    Double consumptionKw = ((Number) row[1]).doubleValue();

                    ShellyConsumption consumption = new ShellyConsumption.Builder()
                            .withPrefix(supply.getShellyMqttPrefix())
                            .withConsumptionKWh(consumptionKw) // Using AVG as approximation for integral
                            .withTimestamp(time)
                            .build();

                    results.add(consumption);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error executing aggregation query for supply {}", supply.getId(), e);
        }

        return results;
    }
}
