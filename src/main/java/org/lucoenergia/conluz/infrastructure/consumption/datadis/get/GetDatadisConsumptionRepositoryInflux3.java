package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import com.influxdb.v3.client.InfluxDBClient;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Repository
@Qualifier("getDatadisConsumptionRepositoryInflux3")
public class GetDatadisConsumptionRepositoryInflux3 implements GetDatadisConsumptionRepository {

    private final InfluxDb3ConnectionManager connectionManager;
    private final DateConverter dateConverter;

    public GetDatadisConsumptionRepositoryInflux3(InfluxDb3ConnectionManager connectionManager,
                                                  DateConverter dateConverter) {
        this.connectionManager = connectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionsByMonth(Supply supply, Month month, int year) {
        String startDate = dateConverter.convertToFirstDayOfTheMonthAsString(month, year);
        String endDate = dateConverter.convertToLastDayOfTheMonthAsString(month, year);

        InfluxDBClient client = connectionManager.getClient();

        String query = String.format("""
                        SELECT
                            DATE_TRUNC('hour', time) as time,
                            SUM(consumption_kwh) AS consumption_kwh,
                            SUM(surplus_energy_kwh) AS surplus_energy_kwh,
                            SUM(self_consumption_energy_kwh) AS self_consumption_energy_kwh,
                            FIRST_VALUE(obtain_method) AS obtain_method,
                            cups
                        FROM "%s"
                        WHERE cups = '%s'
                            AND time >= '%s'
                            AND time <= '%s'
                        GROUP BY DATE_TRUNC('hour', time), cups
                        ORDER BY time
                        """,
                DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                supply.getCode(),
                startDate,
                endDate
        );

        return executeQuery(client, query);
    }

    @Override
    public List<DatadisConsumption> getDailyConsumptionsByRangeOfDates(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate) {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format(
                """
                SELECT
                    DATE_TRUNC('day', time) as time,
                    SUM(consumption_kwh) AS consumption_kwh,
                    SUM(surplus_energy_kwh) AS surplus_energy_kwh,
                    SUM(self_consumption_energy_kwh) AS self_consumption_energy_kwh,
                    FIRST_VALUE(obtain_method) AS obtain_method,
                    cups
                FROM "%s"
                WHERE cups = '%s'
                    AND time >= '%s'
                    AND time <= '%s'
                GROUP BY DATE_TRUNC('day', time), cups
                ORDER BY time
                """,
                DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                supply.getCode(),
                dateConverter.convertToString(startDate),
                dateConverter.convertToString(endDate)
        );

        return executeQuery(client, query);
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionsByRangeOfDates(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate) {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format(
                """
                SELECT
                    DATE_TRUNC('hour', time) as time,
                    SUM(consumption_kwh) AS consumption_kwh,
                    SUM(surplus_energy_kwh) AS surplus_energy_kwh,
                    SUM(self_consumption_energy_kwh) AS self_consumption_energy_kwh,
                    FIRST_VALUE(obtain_method) AS obtain_method,
                    cups
                FROM "%s"
                WHERE cups = '%s'
                    AND time >= '%s'
                    AND time <= '%s'
                GROUP BY DATE_TRUNC('hour', time), cups
                ORDER BY time
                """,
                DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                supply.getCode(),
                dateConverter.convertToString(startDate),
                dateConverter.convertToString(endDate)
        );

        return executeQuery(client, query);
    }

    private List<DatadisConsumption> executeQuery(InfluxDBClient client, String query) {
        List<DatadisConsumption> results = new ArrayList<>();

        try (Stream<Object[]> stream = client.query(query)) {
            stream.forEach(row -> {
                DatadisConsumption consumption = new DatadisConsumption();

                // Row columns depend on the SELECT statement
                // Expected columns: time, cups, consumption_kwh, surplus_energy_kwh, self_consumption_energy_kwh, obtain_method
                if (row.length > 0 && row[0] != null) {
                    // InfluxDB 3 returns timestamps as Instant or sometimes as BigInteger (nanoseconds)
                    Instant time;
                    if (row[0] instanceof Instant) {
                        time = (Instant) row[0];
                    } else if (row[0] instanceof Number) {
                        long nanos = ((Number) row[0]).longValue();
                        time = Instant.ofEpochSecond(0, nanos);
                    } else {
                        throw new IllegalArgumentException("Unexpected timestamp type: " + row[0].getClass());
                    }
                    consumption.setDate(dateConverter.convertFromInstantToStringDate(time));
                    consumption.setTime(dateConverter.convertFromInstantToStringTime(time));
                }

                if (row.length > 1) consumption.setConsumptionKWh(parseToFloat(row[1]));
                if (row.length > 2) consumption.setSurplusEnergyKWh(parseToFloat(row[2]));
                if (row.length > 3) consumption.setSelfConsumptionEnergyKWh(parseToFloat(row[3]));
                if (row.length > 4) consumption.setObtainMethod(row[4] != null ? row[4].toString() : null);
                if (row.length > 5) consumption.setCups(row[5] != null ? row[5].toString() : null);

                results.add(consumption);
            });
        } catch (Exception e) {
            throw new RuntimeException("Error querying Datadis consumption data", e);
        }

        return results;
    }

    private Float parseToFloat(Object value) {
        if (value == null) {
            return 0.0f;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return 0.0f;
    }
}
