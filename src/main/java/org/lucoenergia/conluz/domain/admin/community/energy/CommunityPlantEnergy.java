package org.lucoenergia.conluz.domain.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;

/**
 * One plant of a community over the period of its energy metrics: its installed power, its
 * production and its share of the community's production.
 */
public class CommunityPlantEnergy {

    private final PlantReference plant;
    private final double installedPowerKWp;
    private final Double productionKWh;
    private final Double productionShare;

    CommunityPlantEnergy(PlantReference plant, double installedPowerKWp, Double productionKWh,
                         Double productionShare) {
        this.plant = plant;
        this.installedPowerKWp = installedPowerKWp;
        this.productionKWh = productionKWh;
        this.productionShare = productionShare;
    }

    public PlantReference getPlant() {
        return plant;
    }

    /**
     * The plant's installed power, in kWp.
     */
    public double getInstalledPowerKWp() {
        return installedPowerKWp;
    }

    /**
     * The production the plant stored over the period, unrounded, in kWh; null when it stored no
     * production record in the period, which is not a measured zero.
     */
    public Double getProductionKWh() {
        return productionKWh;
    }

    /**
     * The plant's production divided by the community's, on a 0-1 scale; null when the plant stored
     * no production record or the community produced nothing.
     */
    public Double getProductionShare() {
        return productionShare;
    }
}
