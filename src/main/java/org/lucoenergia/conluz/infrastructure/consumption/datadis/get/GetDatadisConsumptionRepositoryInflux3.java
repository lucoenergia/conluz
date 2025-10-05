package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import com.influxdb.v3.client.InfluxDBClient;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Month;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

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

        String query = String.format(
                "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
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
                    LAST(obtain_method) AS obtain_method,
                    cups
                FROM "%s"
                WHERE cups = '%s'
                    AND time >= '%s'
                    AND time <= '%s'
                GROUP BY DATE_TRUNC('day', time), cups
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
                    LAST(obtain_method) AS obtain_method,
                    cups
                FROM "%s"
                WHERE cups = '%s'
                    AND time >= '%s'
                    AND time <= '%s'
                GROUP BY DATE_TRUNC('hour', time), cups
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

        try (FlightStream stream = client.query(query)) {
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                // TODO: Implement proper mapping from Arrow vectors to DatadisConsumption
                // This requires extracting values from the Arrow vector schema
            }
        } catch (Exception e) {
            throw new RuntimeException("Error querying Datadis consumption data", e);
        }

        return results;
    }
}
