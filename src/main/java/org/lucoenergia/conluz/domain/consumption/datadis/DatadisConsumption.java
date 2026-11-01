package org.lucoenergia.conluz.domain.consumption.datadis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * Represents consumption data from the external Datadis API.
 * The annotation ignores unknown properties to ensure resilience when the API adds new fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatadisConsumption {

    private String cups;
    private String date;
    /**
     * A local time, {@code HH:mm}, that goes with {@code date}.
     * It means two different things depending on where the object comes from:
     * <ul>
     *     <li>Read from Datadis, it is the label of an hourly record: the local time at which the record's hour
     *     <em>ends</em>, from {@code 01:00} to {@code 24:00}, with no offset (see #322).</li>
     *     <li>Returned by this application, it is the start of the period the values cover: for an hourly series,
     *     the local time at which the hour starts. Each operation's description says what it holds.</li>
     * </ul>
     */
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
