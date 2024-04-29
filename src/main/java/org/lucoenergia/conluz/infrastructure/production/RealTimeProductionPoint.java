package org.lucoenergia.conluz.infrastructure.production;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.time.Instant;

@Measurement(name = HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT)
public class RealTimeProductionPoint {

    @Column(name = "time")
    private Instant time;
    /**
     * Plant ID
     */
    @Column(name = "station_code")
    private String stationCode;
    /**
     * Plant health status.
     * The following plant health states are supported:
     * 1: disconnected
     * 2: faulty
     * 3: healthy
     */
    @Column(name = "real_health_state")
    private int realHealthState;
    /**
     * Yield today (kWh)
     */
    @Column(name = "day_power")
    private double dayPower;
    /**
     * Total yield (kWh)
     */
    @Column(name = "total_power")
    private double totalPower;
    /**
     * Revenue today, in the currency specified in the management system.
     */
    @Column(name = "day_income")
    private double dayIncome;
    /**
     * Yield this month (kWh)
     */
    @Column(name = "month_power")
    private double monthPower;
    /**
     * Total revenue, in the currency specified in the management system.
     */
    @Column(name = "total_income")
    private double totalIncome;

    public Instant getTime() {
        return time;
    }

    public String getStationCode() {
        return stationCode;
    }

    public int getRealHealthState() {
        return realHealthState;
    }

    public double getDayPower() {
        return dayPower;
    }

    public double getTotalPower() {
        return totalPower;
    }

    public double getDayIncome() {
        return dayIncome;
    }

    public double getMonthPower() {
        return monthPower;
    }

    public double getTotalIncome() {
        return totalIncome;
    }


    public static class Builder {
        private Instant time;
        private String stationCode;
        private int realHealthState;
        private double dayPower;
        private double totalPower;
        private double dayIncome;
        private double monthPower;
        private double totalIncome;

        public Builder setTime(Instant time) {
            this.time = time;
            return this;
        }

        public Builder setStationCode(String stationCode) {
            this.stationCode = stationCode;
            return this;
        }

        public Builder setRealHealthState(int realHealthState) {
            this.realHealthState = realHealthState;
            return this;
        }

        public Builder setDayPower(double dayPower) {
            this.dayPower = dayPower;
            return this;
        }

        public Builder setTotalPower(double totalPower) {
            this.totalPower = totalPower;
            return this;
        }

        public Builder setDayIncome(double dayIncome) {
            this.dayIncome = dayIncome;
            return this;
        }

        public Builder setMonthPower(double monthPower) {
            this.monthPower = monthPower;
            return this;
        }

        public Builder setTotalIncome(double totalIncome) {
            this.totalIncome = totalIncome;
            return this;
        }

        public RealTimeProductionPoint build() {
            RealTimeProductionPoint realTimeProduction = new RealTimeProductionPoint();
            realTimeProduction.time = this.time;
            realTimeProduction.stationCode = this.stationCode;
            realTimeProduction.realHealthState = this.realHealthState;
            realTimeProduction.dayPower = this.dayPower;
            realTimeProduction.totalPower = this.totalPower;
            realTimeProduction.dayIncome = this.dayIncome;
            realTimeProduction.monthPower = this.monthPower;
            realTimeProduction.totalIncome = this.totalIncome;
            return realTimeProduction;
        }
    }
}
