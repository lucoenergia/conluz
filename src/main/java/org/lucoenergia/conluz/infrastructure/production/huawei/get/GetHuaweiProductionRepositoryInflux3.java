package org.lucoenergia.conluz.infrastructure.production.huawei.get;

import com.influxdb.v3.client.InfluxDBClient;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Repository
@Qualifier(value = "getHuaweiProductionRepositoryInflux3")
public class GetHuaweiProductionRepositoryInflux3 implements GetHuaweiProductionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetHuaweiProductionRepositoryInflux3.class);

    private final InfluxDb3ConnectionManager connectionManager;
    private final DateConverter dateConverter;

    public GetHuaweiProductionRepositoryInflux3(InfluxDb3ConnectionManager connectionManager,
                                                DateConverter dateConverter) {
        this.connectionManager = connectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<RealTimeProduction> getRealTimeProductionByRangeOfDates(Instant startDate, Instant endDate) {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format(
                """
                SELECT time, station_code, real_health_state, day_power, total_power, day_income, month_power, total_income
                FROM "%s"
                WHERE time >= '%s' AND time <= '%s'
                ORDER BY time
                """,
                HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT,
                dateConverter.convertToString(startDate),
                dateConverter.convertToString(endDate)
        );

        return executeQuery(client, query);
    }

    private List<RealTimeProduction> executeQuery(InfluxDBClient client, String query) {
        List<RealTimeProduction> results = new ArrayList<>();

        try (Stream<Object[]> stream = client.query(query)) {
            stream.forEach(row -> {
                // Expected columns: time, station_code, real_health_state, day_power, total_power, day_income, month_power, total_income
                if (row.length >= 8 && row[0] != null) {
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

                    String stationCode = row[1] != null ? row[1].toString() : null;
                    Integer realHealthState = row[2] != null ? ((Number) row[2]).intValue() : null;
                    Double dayPower = row[3] != null ? ((Number) row[3]).doubleValue() : null;
                    Double totalPower = row[4] != null ? ((Number) row[4]).doubleValue() : null;
                    Double dayIncome = row[5] != null ? ((Number) row[5]).doubleValue() : null;
                    Double monthPower = row[6] != null ? ((Number) row[6]).doubleValue() : null;
                    Double totalIncome = row[7] != null ? ((Number) row[7]).doubleValue() : null;

                    RealTimeProduction production = new RealTimeProduction.Builder()
                            .setTime(time)
                            .setStationCode(stationCode)
                            .setRealHealthState(realHealthState)
                            .setDayPower(dayPower)
                            .setTotalPower(totalPower)
                            .setDayIncome(dayIncome)
                            .setMonthPower(monthPower)
                            .setTotalIncome(totalIncome)
                            .build();

                    results.add(production);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Unable to get real time production by range of dates", e);
        }

        return results;
    }
}
