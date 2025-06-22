package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.infrastructure.production.RealTimeProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@Qualifier(value = "getHuaweiProductionRepositoryInflux")
public class GetHuaweiProductionRepositoryInflux implements GetHuaweiProductionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetHuaweiProductionRepositoryInflux.class);

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetHuaweiProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager, DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<RealTimeProduction> getRealTimeProductionByRangeOfDates(Instant startDate, Instant endDate) {

        List<RealTimeProduction> result = new ArrayList<>();

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM %s WHERE time >= '%s' AND time <= '%s'",
                    HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT,
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult queryResult = connection.query(query);
            if (queryResult.hasError()) {
                LOGGER.error("Query to get real time production by range of dates returned an error: {}", queryResult.getError());
            } else if (queryResult.getResults().isEmpty()) {
                LOGGER.debug("Query to get real time production by range of dates returned an empty result.");
            } else {
                LOGGER.debug("Query to get real time production by range of dates resulted in: {}", queryResult.getResults());
            }

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<RealTimeProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, RealTimeProductionPoint.class);

            result = mapRealTimeProductionPoints(measurementPoints);
        } catch (Exception e) {
            LOGGER.error("Unable to get real time production by range of dates", e);
        }
        return result;
    }

    private List<RealTimeProduction> mapRealTimeProductionPoints(List<RealTimeProductionPoint> points) {
        List<RealTimeProduction> productions = new ArrayList<>();
        for (RealTimeProductionPoint point : points) {
            RealTimeProduction production = new RealTimeProduction.Builder()
                    .setTime(point.getTime())
                    .setStationCode(point.getStationCode())
                    .setRealHealthState(point.getRealHealthState())
                    .setDayPower(point.getDayPower())
                    .setTotalPower(point.getTotalPower())
                    .setDayIncome(point.getDayIncome())
                    .setMonthPower(point.getMonthPower())
                    .setTotalIncome(point.getTotalIncome())
                    .build();
            productions.add(production);
        }
        return productions;
    }
}
