package org.lucoenergia.conluz.domain.production.huawei;

import java.time.OffsetDateTime;

public class RealTimeProduction {

    /**
     * Current system time, in milliseconds
     */
    private OffsetDateTime time;
    /**
     * Plant ID
     */
    private String stationCode;
    /**
     * Plant health status.
     * The following plant health states are supported:
     * 1: disconnected
     * 2: faulty
     * 3: healthy
     */
    private int realHealthState;
    /**
     * Yield today (kWh)
     */
    private double dayPower;
    /**
     * Total yield (kWh)
     */
    private double totalPower;
    /**
     * Revenue today, in the currency specified in the management system.
     */
    private double dayIncome;
    /**
     * Yield this month (kWh)
     */
    private double monthPower;
    /**
     * Total revenue, in the currency specified in the management system.
     */
    private double totalIncome;

    public OffsetDateTime getTime() {
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
        private OffsetDateTime time;
        private String stationCode;
        private int realHealthState;
        private double dayPower;
        private double totalPower;
        private double dayIncome;
        private double monthPower;
        private double totalIncome;

        public Builder setTime(OffsetDateTime time) {
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

        public RealTimeProduction build() {
            RealTimeProduction realTimeProduction = new RealTimeProduction();
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
