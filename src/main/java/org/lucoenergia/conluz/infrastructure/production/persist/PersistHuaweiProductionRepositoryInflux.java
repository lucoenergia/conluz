package org.lucoenergia.conluz.infrastructure.production.persist;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class PersistHuaweiProductionRepositoryInflux implements PersistHuaweiProductionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public PersistHuaweiProductionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                   DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public void persistRealTimeProduction(List<RealTimeProduction> productions) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (RealTimeProduction production : productions) {
                Point point = Point.measurement(HuaweiConfig.HUAWEI_INSTANT_PRODUCTION_MEASUREMENT)
                        .time(dateConverter.convertOffsetDateTimeToMilliseconds(production.getTime()), TimeUnit.MILLISECONDS)
                        .tag("station_code", production.getStationCode())
                        .addField("real_health_state", production.getRealHealthState())
                        .addField("day_power", production.getDayPower())
                        .addField("total_power", production.getTotalPower())
                        .addField("day_income", production.getDayIncome())
                        .addField("month_power", production.getMonthPower())
                        .addField("total_income", production.getTotalIncome())
                        .build();

                batchPoints.point(point);
            }
            connection.write(batchPoints);
        }
    }
}
