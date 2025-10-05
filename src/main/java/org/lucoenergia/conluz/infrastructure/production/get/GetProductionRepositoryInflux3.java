package org.lucoenergia.conluz.infrastructure.production.get;

import com.influxdb.v3.client.InfluxDBClient;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@Qualifier("getProductionRepositoryInflux3")
public class GetProductionRepositoryInflux3 implements GetProductionRepository {

    private final InfluxDb3ConnectionManager connectionManager;
    private final DateConverter dateConverter;
    private final InstantProductionInfluxMapper instantProductionInfluxMapper;
    private final ProductionByHourInfluxMapper productionByHourInfluxMapper;

    public GetProductionRepositoryInflux3(InfluxDb3ConnectionManager connectionManager,
                                          DateConverter dateConverter,
                                          InstantProductionInfluxMapper instantProductionInfluxMapper,
                                          ProductionByHourInfluxMapper productionByHourInfluxMapper) {
        this.connectionManager = connectionManager;
        this.dateConverter = dateConverter;
        this.instantProductionInfluxMapper = instantProductionInfluxMapper;
        this.productionByHourInfluxMapper = productionByHourInfluxMapper;
    }

    @Override
    public InstantProduction getInstantProduction() {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format(
                "SELECT * FROM \"%s\" LIMIT 1",
                HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT
        );

        try (FlightStream stream = client.query(query)) {
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                if (root.getRowCount() > 0) {
                    // Map first row to InstantProduction
                    // TODO: Implement proper mapping from Arrow vectors
                    return new InstantProduction(0.0d);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error querying instant production", e);
        }

        return new InstantProduction(0.0d);
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getHourlyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                    Float partitionCoefficient) {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format(
                "SELECT time, inverter_power * %s as inverter_power FROM \"%s\" WHERE time >= '%s' AND time <= '%s'",
                partitionCoefficient,
                HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                dateConverter.convertToString(startDate),
                dateConverter.convertToString(endDate)
        );

        return executeQuery(client, query);
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getDailyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                   Float partitionCoefficient) {
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate, partitionCoefficient, "day");
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getMonthlyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                     Float partitionCoefficient) {
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate, partitionCoefficient, "month");
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getYearlyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                    Float partitionCoefficient) {
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate, partitionCoefficient, "year");
    }

    private List<ProductionByTime> getProductionByRangeOfDatesGroupedByDuration(OffsetDateTime startDate,
                                                                                OffsetDateTime endDate,
                                                                                Float partitionCoefficient,
                                                                                String precision) {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format(
                "SELECT DATE_TRUNC('%s', time) as time, SUM(inverter_power) * %s as inverter_power " +
                "FROM \"%s\" " +
                "WHERE time >= '%s' AND time <= '%s' " +
                "GROUP BY DATE_TRUNC('%s', time)",
                precision,
                partitionCoefficient,
                HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                dateConverter.convertToString(startDate),
                dateConverter.convertToString(endDate),
                precision
        );

        return executeQuery(client, query);
    }

    private List<ProductionByTime> executeQuery(InfluxDBClient client, String query) {
        List<ProductionByTime> results = new ArrayList<>();

        try (FlightStream stream = client.query(query)) {
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                // TODO: Implement proper mapping from Arrow vectors to ProductionByTime
                // This requires mapping the Arrow vector data to ProductionPoint objects
            }
        } catch (Exception e) {
            throw new RuntimeException("Error querying production data", e);
        }

        return results;
    }
}
