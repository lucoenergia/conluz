package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;

/**
 * Totals of the hourly Datadis consumption records stored for a supply over a period, together
 * with the number of records that contributed to them.
 *
 * <p>The three energy totals are the raw stored fields summed independently. No field is derived
 * from another here; {@link SupplyEnergyMetrics} owns the derived quantities.</p>
 */
public class DatadisConsumptionAggregate {

    private final double consumptionKWh;
    private final double selfConsumptionEnergyKWh;
    private final double surplusEnergyKWh;
    private final long hoursWithData;

    public DatadisConsumptionAggregate(double consumptionKWh, double selfConsumptionEnergyKWh,
                                       double surplusEnergyKWh, long hoursWithData) {
        this.consumptionKWh = consumptionKWh;
        this.selfConsumptionEnergyKWh = selfConsumptionEnergyKWh;
        this.surplusEnergyKWh = surplusEnergyKWh;
        this.hoursWithData = hoursWithData;
    }

    /**
     * The aggregate of an empty set of records: every total zero and no record counted.
     */
    public static DatadisConsumptionAggregate empty() {
        return new DatadisConsumptionAggregate(0d, 0d, 0d, 0L);
    }

    public double getConsumptionKWh() {
        return consumptionKWh;
    }

    public double getSelfConsumptionEnergyKWh() {
        return selfConsumptionEnergyKWh;
    }

    public double getSurplusEnergyKWh() {
        return surplusEnergyKWh;
    }

    public long getHoursWithData() {
        return hoursWithData;
    }
}
