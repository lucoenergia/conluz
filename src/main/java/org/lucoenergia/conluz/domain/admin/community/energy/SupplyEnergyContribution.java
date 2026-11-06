package org.lucoenergia.conluz.domain.admin.community.energy;

/**
 * What one supply with data in a bucket of a community energy series adds to it, unrounded, in kWh.
 */
public record SupplyEnergyContribution(double selfConsumptionKWh, double surplusKWh) {
}
