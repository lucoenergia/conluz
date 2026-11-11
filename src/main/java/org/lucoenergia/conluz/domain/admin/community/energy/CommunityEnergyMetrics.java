package org.lucoenergia.conluz.domain.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.plant.Plant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The energy figures of a community over one period: its plants' production, the self-consumption
 * and surplus of every supply in scope, summed, the shares derived from them, the estimated income of
 * that surplus, the CO₂ the production avoided, the installed power and how many supplies have data.
 *
 * <p>Every figure is kept unrounded; the shares are computed here once, from the unrounded sums, and
 * rounding is left to the response. A figure nothing was stored for is null rather than zero.
 */
public class CommunityEnergyMetrics {

    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;
    private final Double productionKWh;
    private final Double selfConsumptionKWh;
    private final Double surplusKWh;
    private final int supplyCount;
    private final int suppliesWithData;
    private final List<CommunityPlantEnergy> plants;
    private final double installedPowerKWp;
    private final SurplusIncome surplusIncome;
    private final AvoidedEmissions avoidedEmissions;

    private CommunityEnergyMetrics(OffsetDateTime startDate, OffsetDateTime endDate, Double selfConsumptionKWh,
                                   Double surplusKWh, SurplusIncome surplusIncome,
                                   AvoidedEmissions avoidedEmissions, int supplyCount,
                                   int suppliesWithData, List<Plant> plants,
                                   Map<String, Double> productionBySupplyCode) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.selfConsumptionKWh = selfConsumptionKWh;
        this.surplusKWh = surplusKWh;
        this.surplusIncome = surplusIncome;
        this.avoidedEmissions = avoidedEmissions;
        this.supplyCount = supplyCount;
        this.suppliesWithData = suppliesWithData;
        this.productionKWh = plants.stream()
                .map(plant -> productionBySupplyCode.get(plant.getSupply().getCode()))
                .filter(Objects::nonNull)
                .reduce(Double::sum)
                .orElse(null);
        this.plants = plants.stream()
                .map(plant -> {
                    Double production = productionBySupplyCode.get(plant.getSupply().getCode());
                    return new CommunityPlantEnergy(new PlantReference(plant.getId(), plant.getName()),
                            plant.getPeakPowerKWp(), production, share(production, productionKWh));
                })
                .toList();
        this.installedPowerKWp = plants.stream().mapToDouble(Plant::getPeakPowerKWp).sum();
    }

    /**
     * The metrics over a resolved period.
     *
     * @param selfConsumptionKWh     the self-consumption of every supply in scope, summed, or null when
     *                               none of them stored a record in the period
     * @param surplusKWh             the surplus of every supply in scope, summed, or null when none of
     *                               them stored a record in the period
     * @param surplusIncome          the estimated income of that same surplus, or null exactly when
     *                               {@code surplusKWh} is null
     * @param avoidedEmissions       the CO₂ the plants' production avoided, or null when no plant stored
     *                               a production record in the period, or when a year with production is
     *                               earlier than every configured emission factor
     * @param supplyCount            the number of supplies in scope
     * @param suppliesWithData       how many of them have data for the period
     * @param plants                 every plant of the community
     * @param productionBySupplyCode the production each plant stored in the period, keyed by the code
     *                               of the plant's supply; a plant without any record is absent
     */
    public static CommunityEnergyMetrics of(EnergyMetricsPeriod period, Double selfConsumptionKWh,
                                            Double surplusKWh, SurplusIncome surplusIncome,
                                            AvoidedEmissions avoidedEmissions, int supplyCount,
                                            int suppliesWithData, List<Plant> plants,
                                            Map<String, Double> productionBySupplyCode) {
        return new CommunityEnergyMetrics(period.getStartDate(), period.getEndDate(), selfConsumptionKWh,
                surplusKWh, surplusIncome, avoidedEmissions, supplyCount, suppliesWithData, plants,
                productionBySupplyCode);
    }

    /**
     * The metrics when no period could be resolved: null bounds, no figure, no supply with data, and
     * every plant listed with its installed power alone.
     */
    public static CommunityEnergyMetrics withoutPeriod(int supplyCount, List<Plant> plants) {
        return new CommunityEnergyMetrics(null, null, null, null, null, null, supplyCount, 0, plants, Map.of());
    }

    /**
     * {@code part} divided by {@code whole}, or null when either is unknown or the whole is zero.
     */
    private static Double share(Double part, Double whole) {
        if (part == null || whole == null || whole == 0d) {
            return null;
        }
        return part / whole;
    }

    /**
     * The first instant of the period, inclusive, or null when no period could be resolved.
     */
    public OffsetDateTime getStartDate() {
        return startDate;
    }

    /**
     * The end of the period, exclusive, or null when no period could be resolved.
     */
    public OffsetDateTime getEndDate() {
        return endDate;
    }

    /**
     * The production of every plant of the community, summed, in kWh; null when no plant stored a
     * production record in the period.
     */
    public Double getProductionKWh() {
        return productionKWh;
    }

    public Double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public Double getSurplusKWh() {
        return surplusKWh;
    }

    /**
     * The share of the energy assigned to the supplies in scope that they consumed: self-consumption
     * divided by self-consumption plus surplus, on a 0-1 scale; null when both are zero or unknown.
     */
    public Double getSelfConsumptionRatio() {
        return share(selfConsumptionKWh, assignedProductionKWh());
    }

    /**
     * The share of the energy assigned to the supplies in scope that went to the grid: surplus
     * divided by self-consumption plus surplus, on a 0-1 scale; null when both are zero or unknown.
     */
    public Double getSurplusRatio() {
        return share(surplusKWh, assignedProductionKWh());
    }

    /**
     * The estimated income of the surplus at OMIE day-ahead prices, in euros, unrounded; null when the
     * surplus is.
     */
    public BigDecimal getSurplusIncomeEur() {
        return surplusIncome == null ? null : surplusIncome.getAmountEur();
    }

    /**
     * The part of the surplus in hours without a stored OMIE price, in kWh, unrounded; null when the
     * surplus is.
     */
    public Double getUnpricedSurplusKWh() {
        return surplusIncome == null ? null : surplusIncome.getUnpricedSurplusKWh();
    }

    /**
     * The CO₂ the plants' production avoided, in kg CO₂e, unrounded; null when the production is null, or
     * when a year with production is earlier than every configured emission factor.
     */
    public BigDecimal getCo2AvoidedKg() {
        return avoidedEmissions == null ? null : avoidedEmissions.getAmountKg();
    }

    /**
     * The distinct years whose emission factors were applied, in ascending order; null exactly when
     * {@link #getCo2AvoidedKg()} is.
     */
    public List<Integer> getCo2AvoidedFactorYears() {
        return avoidedEmissions == null ? null : avoidedEmissions.getFactorYears();
    }

    private Double assignedProductionKWh() {
        if (selfConsumptionKWh == null || surplusKWh == null) {
            return null;
        }
        return selfConsumptionKWh + surplusKWh;
    }

    /**
     * The installed power of every plant of the community, summed, in kWp.
     */
    public double getInstalledPowerKWp() {
        return installedPowerKWp;
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    public int getSuppliesWithData() {
        return suppliesWithData;
    }

    public List<CommunityPlantEnergy> getPlants() {
        return plants;
    }
}
