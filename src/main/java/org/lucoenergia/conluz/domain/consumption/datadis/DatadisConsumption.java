package org.lucoenergia.conluz.domain.consumption.datadis;

import java.util.Objects;

public class DatadisConsumption {

    private String cups;
    private String date;
    private String time;
    private Float consumptionKWh;
    private String obtainMethod;
    private Float surplusEnergyKWh;
    private Float generationEnergyKWh;
    private Float selfConsumptionEnergyKWh;

    public String getCups() {
        return cups;
    }

    public void setCups(String cups) {
        this.cups = cups;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Float getConsumptionKWh() {
        return consumptionKWh;
    }

    public void setConsumptionKWh(Float consumptionKWh) {
        this.consumptionKWh = consumptionKWh;
    }

    public String getObtainMethod() {
        return obtainMethod;
    }

    public void setObtainMethod(String obtainMethod) {
        this.obtainMethod = obtainMethod;
    }

    public Float getSurplusEnergyKWh() {
        return surplusEnergyKWh;
    }

    public void setSurplusEnergyKWh(Float surplusEnergyKWh) {
        this.surplusEnergyKWh = surplusEnergyKWh;
    }

    public Float getGenerationEnergyKWh() {
        return generationEnergyKWh;
    }

    public void setGenerationEnergyKWh(Float generationEnergyKWh) {
        this.generationEnergyKWh = generationEnergyKWh;
    }

    public Float getSelfConsumptionEnergyKWh() {
        return selfConsumptionEnergyKWh;
    }

    public void setSelfConsumptionEnergyKWh(Float selfConsumptionEnergyKWh) {
        this.selfConsumptionEnergyKWh = selfConsumptionEnergyKWh;
    }

    public boolean isEmpty() {
        return (Objects.isNull(consumptionKWh) || consumptionKWh == 0) &&
                (Objects.isNull(surplusEnergyKWh) || surplusEnergyKWh == 0) &&
                (Objects.isNull(generationEnergyKWh) || generationEnergyKWh == 0) &&
                (Objects.isNull(selfConsumptionEnergyKWh) || selfConsumptionEnergyKWh == 0);
    }

    @Override
    public String toString() {
        return "DatadisConsumption{" +
                "cups='" + cups + '\'' +
                ", date='" + date + '\'' +
                ", time='" + time + '\'' +
                ", consumptionKWh=" + consumptionKWh +
                ", obtainMethod='" + obtainMethod + '\'' +
                ", surplusEnergyKWh=" + surplusEnergyKWh +
                ", generationEnergyKWh=" + generationEnergyKWh +
                ", selfConsumptionEnergyKWh=" + selfConsumptionEnergyKWh +
                '}';
    }
}
