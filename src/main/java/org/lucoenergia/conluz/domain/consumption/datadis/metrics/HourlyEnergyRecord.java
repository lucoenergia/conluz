package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import java.time.Instant;

/**
 * One stored hourly record of a supply, with each energy field exactly as stored: {@code null}
 * when the record does not carry the field, which is not the same as a stored zero.
 */
public class HourlyEnergyRecord {

    private final Instant time;
    private final Double gridImportKWh;
    private final Double selfConsumptionKWh;
    private final Double surplusKWh;

    /**
     * @param gridImportKWh      the stored {@code consumption_kwh}, energy imported from the grid,
     *                           or null when absent
     * @param selfConsumptionKWh the stored {@code self_consumption_energy_kwh}, or null when absent
     * @param surplusKWh         the stored {@code surplus_energy_kwh}, or null when absent
     */
    public HourlyEnergyRecord(Instant time, Double gridImportKWh, Double selfConsumptionKWh, Double surplusKWh) {
        this.time = time;
        this.gridImportKWh = gridImportKWh;
        this.selfConsumptionKWh = selfConsumptionKWh;
        this.surplusKWh = surplusKWh;
    }

    public Instant getTime() {
        return time;
    }

    public Double getGridImportKWh() {
        return gridImportKWh;
    }

    public Double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public Double getSurplusKWh() {
        return surplusKWh;
    }
}
