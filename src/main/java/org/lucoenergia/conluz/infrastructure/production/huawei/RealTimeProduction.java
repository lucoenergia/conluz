package org.lucoenergia.conluz.infrastructure.production.huawei;

public class RealTimeProduction {

    private String stationCode;
    private int realHealthState;
    private double dayPower;
    private double totalPower;
    private double dayIncome;
    private double monthPower;
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
