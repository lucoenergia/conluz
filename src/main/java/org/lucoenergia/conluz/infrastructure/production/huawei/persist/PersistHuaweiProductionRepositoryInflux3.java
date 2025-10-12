package org.lucoenergia.conluz.infrastructure.production.huawei.persist;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Qualifier("persistHuaweiProductionRepositoryInflux3")
public class PersistHuaweiProductionRepositoryInflux3 implements PersistHuaweiProductionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistHuaweiProductionRepositoryInflux3.class);

    private final InfluxDb3ConnectionManager connectionManager;

    public PersistHuaweiProductionRepositoryInflux3(InfluxDb3ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void persistRealTimeProduction(List<RealTimeProduction> productions) {
        InfluxDBClient client = connectionManager.getClient();

        try {
            List<Point> points = new ArrayList<>();
            for (RealTimeProduction production : productions) {
                Point point = Point.measurement(HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT)
                        .setTag("station_code", production.getStationCode())
                        .setField("real_health_state", production.getRealHealthState())
                        .setField("day_power", production.getDayPower())
                        .setField("total_power", production.getTotalPower())
                        .setField("day_income", production.getDayIncome())
                        .setField("month_power", production.getMonthPower())
                        .setField("total_income", production.getTotalIncome())
                        .setTimestamp(production.getTime());

                points.add(point);
            }
            if (!points.isEmpty()) {
                client.writePoints(points);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to persist Huawei real time production.", e);
        }
    }

    @Override
    public void persistHourlyProduction(List<HourlyProduction> productions) {
        InfluxDBClient client = connectionManager.getClient();

        try {
            List<Point> points = new ArrayList<>();
            for (HourlyProduction production : productions) {
                Point point = Point.measurement(HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT)
                        .setTag("station_code", production.getStationCode())
                        .setField(ProductionPoint.INVERTER_POWER, production.getInverterPower())
                        .setField("ongrid_power", production.getOngridPower())
                        .setField("power_profit", production.getPowerProfit())
                        .setField("theory_power", production.getTheoryPower())
                        .setField("radiation_intensity", production.getRadiationIntensity())
                        .setTimestamp(production.getTime());

                points.add(point);
            }
            if (!points.isEmpty()) {
                client.writePoints(points);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to persist Huawei hourly production.", e);
        }
    }
}
