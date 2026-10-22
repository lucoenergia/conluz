package org.lucoenergia.conluz.domain.consumption;

import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisEnergyValues;

import java.util.Objects;

/**
 * One bucket of a supply's consumption series -- a local calendar day or a local calendar month --
 * together with what its self-consumed energy was worth.
 *
 * <p>The two are paired rather than merged into {@link DatadisConsumption} because that class is
 * the shape the external Datadis API speaks, and savings are not something Datadis reports. Pairing
 * also keeps the invariant visible: the amount was priced over the interval this very bucket covers,
 * from this very bucket's energy.
 */
public class SupplyConsumptionBucket {

    private final DatadisConsumption consumption;
    private final SupplySavings savings;

    private SupplyConsumptionBucket(DatadisConsumption consumption, SupplySavings savings) {
        this.consumption = consumption;
        this.savings = savings;
    }

    public static SupplyConsumptionBucket of(DatadisConsumption consumption, SupplySavings savings) {
        return new SupplyConsumptionBucket(Objects.requireNonNull(consumption),
                Objects.requireNonNull(savings));
    }

    public DatadisConsumption getConsumption() {
        return consumption;
    }

    public SupplySavings getSavings() {
        return savings;
    }

    /**
     * The energy assigned to the supply over the bucket, derived from the bucket's self-consumed
     * and surplus energy as reported, unrounded. The distributor's generation figure plays no part
     * in it (#460).
     */
    public double getAssignedProductionKWh() {
        return EnergyBalance.assignedProductionKWh(
                DatadisEnergyValues.exactlyAsReported(consumption.getSelfConsumptionEnergyKWh()).doubleValue(),
                DatadisEnergyValues.exactlyAsReported(consumption.getSurplusEnergyKWh()).doubleValue());
    }

    @Override
    public String toString() {
        return "SupplyConsumptionBucket{" +
                "consumption=" + consumption +
                ", savings=" + savings +
                '}';
    }
}
