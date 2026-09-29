package org.lucoenergia.conluz.domain.consumption;

/**
 * The three stored energy totals of a period, together with the quantities derived from them: the
 * total consumption, the assigned production and the self-sufficiency / self-consumption ratios.
 *
 * <p>The ratios are computed by summing every record first and dividing once, never by averaging
 * per-record ratios: an hour consuming 100 kWh and an hour consuming 1 kWh must not weigh the
 * same. The same holds across supplies: {@link #plus(EnergyBalance)} adds the raw totals, and the
 * ratios of the sum are derived from those sums, never from the ratios of the parts.</p>
 */
public class EnergyBalance {

    private final double gridImportKWh;
    private final double selfConsumptionKWh;
    private final double surplusKWh;
    private final double totalConsumptionKWh;
    private final double assignedProductionKWh;
    private final Double selfSufficiencyRatio;
    private final Double selfConsumptionRatio;

    /**
     * @param gridImportKWh      the sum of the stored {@code consumption_kwh} field, which is
     *                           energy imported from the grid and excludes self-consumed energy
     * @param selfConsumptionKWh the sum of the stored {@code self_consumption_energy_kwh} field
     * @param surplusKWh         the sum of the stored {@code surplus_energy_kwh} field
     */
    public EnergyBalance(double gridImportKWh, double selfConsumptionKWh, double surplusKWh) {
        this.gridImportKWh = gridImportKWh;
        this.selfConsumptionKWh = selfConsumptionKWh;
        this.surplusKWh = surplusKWh;
        this.totalConsumptionKWh = gridImportKWh + selfConsumptionKWh;
        this.assignedProductionKWh = selfConsumptionKWh + surplusKWh;
        this.selfSufficiencyRatio = ratio(selfConsumptionKWh, this.totalConsumptionKWh);
        this.selfConsumptionRatio = ratio(selfConsumptionKWh, this.assignedProductionKWh);
    }

    /**
     * The balance of a period without any energy: every total zero and both ratios null.
     */
    public static EnergyBalance empty() {
        return new EnergyBalance(0d, 0d, 0d);
    }

    /**
     * The balance of both sets of records together. The raw totals are added and the ratios are
     * derived again from the sums.
     */
    public EnergyBalance plus(EnergyBalance other) {
        return new EnergyBalance(
                gridImportKWh + other.gridImportKWh,
                selfConsumptionKWh + other.selfConsumptionKWh,
                surplusKWh + other.surplusKWh);
    }

    /**
     * Divides only once the denominator is known to be non-zero, so a ratio is either a finite
     * number or null. Dividing first would yield {@code NaN} or {@code Infinity}, which Jackson
     * serialises as bare tokens that are not valid JSON.
     */
    private static Double ratio(double numerator, double denominator) {
        if (denominator == 0d) {
            return null;
        }
        return numerator / denominator;
    }

    public double getGridImportKWh() {
        return gridImportKWh;
    }

    public double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public double getSurplusKWh() {
        return surplusKWh;
    }

    public double getTotalConsumptionKWh() {
        return totalConsumptionKWh;
    }

    public double getAssignedProductionKWh() {
        return assignedProductionKWh;
    }

    public Double getSelfSufficiencyRatio() {
        return selfSufficiencyRatio;
    }

    public Double getSelfConsumptionRatio() {
        return selfConsumptionRatio;
    }
}
