package org.lucoenergia.conluz.infrastructure.production.huawei;

public class RealTimeProduction {

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

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public int getRealHealthState() {
        return realHealthState;
    }

    public void setRealHealthState(int realHealthState) {
        this.realHealthState = realHealthState;
    }

    public double getDayPower() {
        return dayPower;
    }

    public void setDayPower(double dayPower) {
        this.dayPower = dayPower;
    }

    public double getTotalPower() {
        return totalPower;
    }

    public void setTotalPower(double totalPower) {
        this.totalPower = totalPower;
    }

    public double getDayIncome() {
        return dayIncome;
    }

    public void setDayIncome(double dayIncome) {
        this.dayIncome = dayIncome;
    }

    public double getMonthPower() {
        return monthPower;
    }

    public void setMonthPower(double monthPower) {
        this.monthPower = monthPower;
    }

    public double getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(double totalIncome) {
        this.totalIncome = totalIncome;
    }
}
