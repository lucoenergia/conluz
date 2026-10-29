package org.lucoenergia.conluz.domain.consumption;

import java.util.List;

/**
 * The share of the energy assigned to some supplies that they consumed rather than fed back to the
 * grid, together with the energy it was computed from and the plant supplies it leaves out.
 *
 * <p>A plant's own supply never takes part (SEM-003, ENM-007): the Datadis sync stores its surplus
 * as 0 ({@code DatadisSyncServiceImpl#copyWithZeroSurplus}), so its ratio would always be 1. The plant
 * link, not any figure, says which supplies those are. The energy of the remaining supplies is summed first and divided once (ENM-006), never
 * averaged from per-supply ratios.</p>
 *
 * <p>This is the single place where the exclusion and the precedence of the statuses are decided:
 * {@link SelfConsumptionRatioStatus#NOT_APPLICABLE} wins over
 * {@link SelfConsumptionRatioStatus#NOTHING_ASSIGNED}.</p>
 */
public final class SelfConsumptionRatio {

    private final double selfConsumptionKWh;
    private final double assignedProductionKWh;
    private final int excludedPlantSupplyCount;
    private final SelfConsumptionRatioStatus status;
    private final Double value;

    private SelfConsumptionRatio(double selfConsumptionKWh, double assignedProductionKWh,
                                 int excludedPlantSupplyCount, SelfConsumptionRatioStatus status, Double value) {
        this.selfConsumptionKWh = selfConsumptionKWh;
        this.assignedProductionKWh = assignedProductionKWh;
        this.excludedPlantSupplyCount = excludedPlantSupplyCount;
        this.status = status;
        this.value = value;
    }

    /**
     * The ratio over the given supplies.
     *
     * @param regularSupplies  the energy balance of every supply in scope that is no plant's own supply
     * @param plantSupplyCount how many supplies in scope are a plant's own supply, left out of the ratio
     */
    public static SelfConsumptionRatio of(List<EnergyBalance> regularSupplies, int plantSupplyCount) {
        if (regularSupplies.isEmpty() && plantSupplyCount > 0) {
            return new SelfConsumptionRatio(0d, 0d, plantSupplyCount, SelfConsumptionRatioStatus.NOT_APPLICABLE, null);
        }
        double selfConsumptionKWh = 0d;
        double surplusKWh = 0d;
        for (EnergyBalance supply : regularSupplies) {
            selfConsumptionKWh += supply.getSelfConsumptionKWh();
            surplusKWh += supply.getSurplusKWh();
        }
        double assignedProductionKWh = EnergyBalance.assignedProductionKWh(selfConsumptionKWh, surplusKWh);
        // Compared before dividing, so the ratio is a finite number or null, never NaN or Infinity.
        if (assignedProductionKWh == 0d) {
            return new SelfConsumptionRatio(selfConsumptionKWh, assignedProductionKWh, plantSupplyCount,
                    SelfConsumptionRatioStatus.NOTHING_ASSIGNED, null);
        }
        return new SelfConsumptionRatio(selfConsumptionKWh, assignedProductionKWh, plantSupplyCount,
                SelfConsumptionRatioStatus.AVAILABLE, selfConsumptionKWh / assignedProductionKWh);
    }

    /**
     * The ratio of a single supply.
     *
     * @param plantSupply whether the supply is a plant's own supply
     */
    public static SelfConsumptionRatio ofSupply(EnergyBalance supply, boolean plantSupply) {
        return plantSupply ? of(List.of(), 1) : of(List.of(supply), 0);
    }

    /**
     * The ratio, on a 0-1 scale, or null unless the status is {@link SelfConsumptionRatioStatus#AVAILABLE}.
     */
    public Double getValue() {
        return value;
    }

    public SelfConsumptionRatioStatus getStatus() {
        return status;
    }

    /**
     * The unrounded self-consumed energy the ratio was computed from: that of the supplies it covers.
     */
    public double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    /**
     * The unrounded energy assigned to the supplies the ratio covers, which the ratio divides by.
     */
    public double getAssignedProductionKWh() {
        return assignedProductionKWh;
    }

    /**
     * How many plant's own supplies the ratio leaves out.
     */
    public int getExcludedPlantSupplyCount() {
        return excludedPlantSupplyCount;
    }
}
