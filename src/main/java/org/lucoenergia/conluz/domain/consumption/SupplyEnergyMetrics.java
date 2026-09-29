package org.lucoenergia.conluz.domain.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.OffsetDateTime;

/**
 * A supply's energy totals and self-sufficiency / self-consumption ratios over a resolved period.
 *
 * <p>The ratios are computed by summing every record first and dividing once, never by averaging
 * per-record ratios: an hour consuming 100 kWh and an hour consuming 1 kWh must not weigh the
 * same.</p>
 *
 * <p>Both period bounds are null when the supply has no stored record and no explicit period was
 * requested.</p>
 *
 * <p>{@link #getSavings()} is always present, and reports what the self-consumed energy was worth
 * over the same period the energy totals cover.</p>
 */
public class SupplyEnergyMetrics {

    private final Supply supply;
    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;
    private final long hoursWithData;
    private final long expectedHours;
    private final EnergyBalance energyBalance;
    private final SupplySavings savings;

    /**
     * @param gridImportKWh      the sum of the stored {@code consumption_kwh} field, which is
     *                           energy imported from the grid and excludes self-consumed energy
     * @param selfConsumptionKWh the sum of the stored {@code self_consumption_energy_kwh} field
     * @param surplusKWh         the sum of the stored {@code surplus_energy_kwh} field
     * @param savings            what {@code selfConsumptionKWh} was worth over this same period
     */
    public SupplyEnergyMetrics(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                               long hoursWithData, long expectedHours, double gridImportKWh,
                               double selfConsumptionKWh, double surplusKWh,
                               SupplySavings savings) {
        this.supply = supply;
        this.startDate = startDate;
        this.endDate = endDate;
        this.hoursWithData = hoursWithData;
        this.expectedHours = expectedHours;
        this.energyBalance = new EnergyBalance(gridImportKWh, selfConsumptionKWh, surplusKWh);
        this.savings = savings;
    }

    /**
     * The metrics of a supply with no record in the resolved period, over the given period. The
     * savings are {@link SupplySavings#unpriced()}: with no period there is nothing to price.
     */
    public static SupplyEnergyMetrics empty(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                                            long expectedHours) {
        return new SupplyEnergyMetrics(supply, startDate, endDate, 0L, expectedHours, 0d, 0d, 0d,
                SupplySavings.unpriced());
    }

    public Supply getSupply() {
        return supply;
    }

    public OffsetDateTime getStartDate() {
        return startDate;
    }

    public OffsetDateTime getEndDate() {
        return endDate;
    }

    public long getHoursWithData() {
        return hoursWithData;
    }

    public long getExpectedHours() {
        return expectedHours;
    }

    public double getGridImportKWh() {
        return energyBalance.getGridImportKWh();
    }

    public double getSelfConsumptionKWh() {
        return energyBalance.getSelfConsumptionKWh();
    }

    public double getSurplusKWh() {
        return energyBalance.getSurplusKWh();
    }

    public double getTotalConsumptionKWh() {
        return energyBalance.getTotalConsumptionKWh();
    }

    public double getAssignedProductionKWh() {
        return energyBalance.getAssignedProductionKWh();
    }

    public Double getSelfSufficiencyRatio() {
        return energyBalance.getSelfSufficiencyRatio();
    }

    public Double getSelfConsumptionRatio() {
        return energyBalance.getSelfConsumptionRatio();
    }

    public EnergyBalance getEnergyBalance() {
        return energyBalance;
    }

    public SupplySavings getSavings() {
        return savings;
    }
}
