package org.lucoenergia.conluz.infrastructure.production.get;

import com.influxdb.v3.client.InfluxDBClient;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3Duration;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Repository
@Qualifier("getProductionRepositoryInflux3")
public class GetProductionRepositoryInflux3 implements GetProductionRepository {

    private final InfluxDb3ConnectionManager connectionManager;
    private final DateConverter dateConverter;
    private final ProductionByHourInfluxMapper productionByHourInfluxMapper;

    public GetProductionRepositoryInflux3(InfluxDb3ConnectionManager connectionManager,
                                          DateConverter dateConverter,
                                          ProductionByHourInfluxMapper productionByHourInfluxMapper) {
        this.connectionManager = connectionManager;
        this.dateConverter = dateConverter;
        this.productionByHourInfluxMapper = productionByHourInfluxMapper;
    }

    @Override
    public InstantProduction getInstantProduction() {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format(
                "SELECT inverter_power FROM \"%s\" ORDER BY time DESC LIMIT 1",
                HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT
        );

        try (Stream<Object[]> stream = client.query(query)) {
            return stream.findFirst()
                    .map(row -> {
                        if (row.length > 0 && row[0] != null) {
                            Double power = ((Number) row[0]).doubleValue();
                            return new InstantProduction(power);
                        }
                        return new InstantProduction(0.0d);
                    })
                    .orElse(new InstantProduction(0.0d));
        } catch (Exception e) {
            throw new RuntimeException("Error querying instant production", e);
        }
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
                "SELECT time, inverter_power * %s as inverter_power FROM \"%s\" WHERE time >= '%s' AND time <= '%s' ORDER BY time",
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
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate, partitionCoefficient, InfluxDb3Duration.DAILY);
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getMonthlyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                     Float partitionCoefficient) {
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate, partitionCoefficient, InfluxDb3Duration.MONTHLY);
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getYearlyProductionByRangeOfDates(startDate, endDate, 1f);
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                    Float partitionCoefficient) {
        return getProductionByRangeOfDatesGroupedByDuration(startDate, endDate, partitionCoefficient, InfluxDb3Duration.YEARLY);
    }

    private List<ProductionByTime> getProductionByRangeOfDatesGroupedByDuration(OffsetDateTime startDate,
                                                                                OffsetDateTime endDate,
                                                                                Float partitionCoefficient,
                                                                                String precision) {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format("""
                SELECT DATE_TRUNC('%s', time) as time, SUM(inverter_power) * %s as inverter_power
                FROM "%s"
                WHERE time >= '%s' AND time <= '%s'
                GROUP BY DATE_TRUNC('%s', time)
                ORDER BY time
                """,
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

        try (Stream<Object[]> stream = client.query(query)) {
            stream.forEach(row -> {
                // Expected columns: time, inverter_power
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    // InfluxDB 3 returns timestamps as Instant objects
                    Instant time;
                    if (row[0] instanceof Instant) {
                        time = (Instant) row[0];
                    } else if (row[0] instanceof Number) {
                        // Sometimes timestamps come as BigInteger (nanoseconds since epoch)
                        long nanos = ((Number) row[0]).longValue();
                        time = Instant.ofEpochSecond(0, nanos);
                    } else {
                        throw new IllegalArgumentException("Unexpected timestamp type: " + row[0].getClass());
                    }

                    Double inverterPower = ((Number) row[1]).doubleValue();

                    ProductionPoint point = new ProductionPoint(time, inverterPower);
                    results.add(productionByHourInfluxMapper.map(point));
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error querying production data", e);
        }

        return results;
    }
}
