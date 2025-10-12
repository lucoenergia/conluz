package org.lucoenergia.conluz.infrastructure.consumption.shelly.get;

import com.influxdb.v3.client.InfluxDBClient;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionPoint;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyMqttPowerMessagePoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
@Qualifier("getShellyConsumptionRepositoryInflux3")
public class GetShellyConsumptionRepositoryInflux3 implements GetShellyConsumptionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetShellyConsumptionRepositoryInflux3.class);

    private final InfluxDb3ConnectionManager connectionManager;
    private final DateConverter dateConverter;

    public GetShellyConsumptionRepositoryInflux3(InfluxDb3ConnectionManager connectionManager,
                                                 DateConverter dateConverter) {
        this.connectionManager = connectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<ShellyInstantConsumption> getHourlyConsumptionsByRangeOfDatesAndSupply(Supply supply,
                                                                                        OffsetDateTime startDate,
                                                                                        OffsetDateTime endDate) {
        InfluxDBClient client = connectionManager.getClient();

        String startDateAsString = dateConverter.convertToString(startDate);
        String endDateAsString = dateConverter.convertToString(endDate);

        LOGGER.info("Getting hourly consumptions from {} to {} for the supply {}", startDateAsString, endDateAsString,
                supply.getShellyId());

        String query = String.format(
                """
                SELECT time, prefix, channel, consumption_kw
                FROM "%s"
                WHERE prefix = '%s' AND time >= '%s' AND time <= '%s'
                ORDER BY time
                """,
                ShellyInstantConsumptionPoint.MEASUREMENT,
                supply.getShellyMqttPrefix(),
                startDateAsString,
                endDateAsString
        );

        return executeInstantConsumptionQuery(client, query);
    }

    @Override
    public List<ShellyInstantConsumption> getAllInstantConsumptions() {
        InfluxDBClient client = connectionManager.getClient();

        LOGGER.info("Getting all instant consumptions");

        String query = String.format(
                "SELECT time, prefix, channel, consumption_kw FROM \"%s\" ORDER BY time",
                ShellyInstantConsumptionPoint.MEASUREMENT
        );

        return executeInstantConsumptionQuery(client, query);
    }

    @Override
    public List<ShellyConsumption> getAllConsumptions() {
        InfluxDBClient client = connectionManager.getClient();

        LOGGER.info("Getting all consumptions");

        String query = String.format(
                "SELECT time, prefix, consumption_kwh FROM \"%s\" ORDER BY time",
                ShellyConsumptionPoint.MEASUREMENT
        );

        return executeConsumptionQuery(client, query);
    }

    @Override
    public List<ShellyInstantConsumption> getShellyMqttPowerMessagesByRangeOfDates(OffsetDateTime startDate,
                                                                                    OffsetDateTime endDate) {
        InfluxDBClient client = connectionManager.getClient();

        String startDateAsString = dateConverter.convertToString(startDate);
        String endDateAsString = dateConverter.convertToString(endDate);

        LOGGER.info("Getting MQTT power messages from {} to {}.", startDateAsString, endDateAsString);

        String query = String.format(
                """
                SELECT time, topic, value
                FROM "%s"
                WHERE time >= '%s' AND time <= '%s'
                ORDER BY time
                """,
                ShellyMqttPowerMessagePoint.MEASUREMENT,
                startDateAsString,
                endDateAsString
        );

        return executeMqttPowerMessageQuery(client, query);
    }

    private List<ShellyInstantConsumption> executeInstantConsumptionQuery(InfluxDBClient client, String query) {
        List<ShellyInstantConsumption> results = new ArrayList<>();

        try (Stream<Object[]> stream = client.query(query)) {
            stream.forEach(row -> {
                // Expected columns: time, prefix, channel, consumption_kw
                if (row.length >= 4 && row[0] != null) {
                    Instant time = parseTimestamp(row[0]);
                    String prefix = row[1] != null ? row[1].toString() : null;
                    String channel = row[2] != null ? row[2].toString() : null;
                    Double consumptionKw = row[3] != null ? ((Number) row[3]).doubleValue() : null;

                    ShellyInstantConsumption consumption = new ShellyInstantConsumption.Builder()
                            .withPrefix(prefix)
                            .withChannel(channel)
                            .withConsumptionKW(consumptionKw)
                            .withTimestamp(time)
                            .build();

                    results.add(consumption);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Unable to execute instant consumption query", e);
        }

        return results;
    }

    private List<ShellyConsumption> executeConsumptionQuery(InfluxDBClient client, String query) {
        List<ShellyConsumption> results = new ArrayList<>();

        try (Stream<Object[]> stream = client.query(query)) {
            stream.forEach(row -> {
                // Expected columns: time, prefix, consumption_kwh
                if (row.length >= 3 && row[0] != null) {
                    Instant time = parseTimestamp(row[0]);
                    String prefix = row[1] != null ? row[1].toString() : null;
                    Double consumptionKwh = row[2] != null ? ((Number) row[2]).doubleValue() : null;

                    ShellyConsumption consumption = new ShellyConsumption.Builder()
                            .withPrefix(prefix)
                            .withConsumptionKWh(consumptionKwh)
                            .withTimestamp(time)
                            .build();

                    results.add(consumption);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Unable to execute consumption query", e);
        }

        return results;
    }

    private List<ShellyInstantConsumption> executeMqttPowerMessageQuery(InfluxDBClient client, String query) {
        List<ShellyInstantConsumption> results = new ArrayList<>();

        try (Stream<Object[]> stream = client.query(query)) {
            stream.forEach(row -> {
                // Expected columns: time, topic, value
                if (row.length >= 3 && row[0] != null) {
                    Instant time = parseTimestamp(row[0]);
                    String topic = row[1] != null ? row[1].toString() : null;
                    Double value = row[2] != null ? ((Number) row[2]).doubleValue() : null;

                    Optional<ShellyInstantConsumption> consumption = parseMessageToConsumption(time, topic, value);
                    consumption.ifPresent(results::add);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Unable to execute MQTT power message query", e);
        }

        return results;
    }

    private Optional<ShellyInstantConsumption> parseMessageToConsumption(Instant time, String topic, Double value) {
        if (topic == null || value == null) {
            return Optional.empty();
        }

        Double consumptionKw = convertFromWToKW(value);
        if (consumptionKw > 0) {
            String prefix = getPrefix(topic);
            String channel = getChannel(topic);

            return Optional.of(new ShellyInstantConsumption.Builder()
                    .withConsumptionKW(consumptionKw)
                    .withTimestamp(time)
                    .withPrefix(prefix)
                    .withChannel(channel)
                    .build());
        }
        return Optional.empty();
    }

    private Instant parseTimestamp(Object timestampObj) {
        if (timestampObj instanceof Instant) {
            return (Instant) timestampObj;
        } else if (timestampObj instanceof Number) {
            long nanos = ((Number) timestampObj).longValue();
            return Instant.ofEpochSecond(0, nanos);
        } else {
            throw new IllegalArgumentException("Unexpected timestamp type: " + timestampObj.getClass());
        }
    }

    private Double convertFromWToKW(Double energyInW) {
        return energyInW / 1000;
    }

    private String getPrefix(String topic) {
        String[] slices = topic.split("/");
        if (slices.length >= 3) {
            return slices[1] + "/" + slices[2];
        }
        return null;
    }

    private String getChannel(String topic) {
        String[] slices = topic.split("/");
        if (slices.length >= 5) {
            return slices[4];
        }
        return null;
    }
}
