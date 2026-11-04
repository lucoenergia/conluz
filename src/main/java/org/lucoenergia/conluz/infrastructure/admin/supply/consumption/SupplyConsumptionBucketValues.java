package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisEnergyValues;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The values of a supply consumption bucket as every series response presents them, mapped once so
 * that each response carries them rounded and derived the same way.
 *
 * <p>The energy figures are rounded here to 3 decimals, and only here: what is stored, and every
 * sum built from it, keeps full precision.
 *
 * <p>This is also where the amount is rounded, once, for presentation: the calculation itself keeps
 * full precision, so a bucket cut across two tariff segments is rounded as a whole rather than once
 * per segment. Rounding per bucket does mean the rounded buckets of a period need not add up to the
 * cent to the single rounded total the energy metrics endpoint reports for that same period.
 */
record SupplyConsumptionBucketValues(String cups, String date, String time, Float gridImportKWh,
                                     String obtainMethod, Float surplusKWh, Float selfConsumptionKWh,
                                     Float assignedProductionKWh, BigDecimal savingsEur,
                                     TariffSource tariffSource) {

    private static final int CENTS = 2;

    static SupplyConsumptionBucketValues of(SupplyConsumptionBucket bucket) {
        DatadisConsumption consumption = bucket.getConsumption();
        return new SupplyConsumptionBucketValues(
                consumption.getCups(),
                consumption.getDate(),
                consumption.getTime(),
                rounded(consumption.getConsumptionKWh()),
                consumption.getObtainMethod(),
                rounded(consumption.getSurplusEnergyKWh()),
                rounded(consumption.getSelfConsumptionEnergyKWh()),
                (float) KWhRounding.round(bucket.getAssignedProductionKWh()),
                bucket.getSavings().getAmountEur().setScale(CENTS, RoundingMode.HALF_UP),
                bucket.getSavings().getTariffSource());
    }

    /**
     * Rounds the figure as reported -- read through the float's shortest decimal representation --
     * rather than its widened binary value, which can sit just below a tie.
     */
    private static Float rounded(Float kWh) {
        return (float) KWhRounding.round(DatadisEnergyValues.exactlyAsReported(kWh).doubleValue());
    }
}
