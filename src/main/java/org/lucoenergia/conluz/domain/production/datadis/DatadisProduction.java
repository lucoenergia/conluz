package org.lucoenergia.conluz.domain.production.datadis;

/**
 * Represents production data derived from the surplus energy reported by the external Datadis API
 * for supplies that back a plant. The {@code date} and {@code time} fields mirror the original
 * consumption record so the production time-series shares the exact same timestamps.
 */
public class DatadisProduction {

    private String cups;
    private String date;
    private String time;
    private Float productionKWh;
    private String obtainMethod;

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

    public Float getProductionKWh() {
        return productionKWh;
    }

    public void setProductionKWh(Float productionKWh) {
        this.productionKWh = productionKWh;
    }

    public String getObtainMethod() {
        return obtainMethod;
    }

    public void setObtainMethod(String obtainMethod) {
        this.obtainMethod = obtainMethod;
    }

    @Override
    public String toString() {
        return "DatadisProduction{" +
                "cups='" + cups + '\'' +
                ", date='" + date + '\'' +
                ", time='" + time + '\'' +
                ", productionKWh=" + productionKWh +
                ", obtainMethod='" + obtainMethod + '\'' +
                '}';
    }
}
