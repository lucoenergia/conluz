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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@Qualifier(value = "getHuaweiProductionRepositoryInflux")
public class GetHuaweiProductionRepositoryInflux implements GetHuaweiProductionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetHuaweiProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager, DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<RealTimeProduction> getRealTimeProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM %s WHERE time >= '%s' AND time <= '%s'",
                    HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT,
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<RealTimeProductionPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, RealTimeProductionPoint.class);

            return mapRealTimeProductionPoints(measurementPoints);
        }
    }

    public List<RealTimeProduction> mapRealTimeProductionPoints(List<RealTimeProductionPoint> points) {
        List<RealTimeProduction> productions = new ArrayList<>();
        for (RealTimeProductionPoint point : points) {
            RealTimeProduction production = new RealTimeProduction.Builder()
                    .setTime(dateConverter.convertInstantToOffsetDateTime(point.getTime()))
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
