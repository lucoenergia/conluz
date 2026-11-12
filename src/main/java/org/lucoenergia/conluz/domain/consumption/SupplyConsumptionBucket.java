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
    private final boolean publishedData;

    private SupplyConsumptionBucket(DatadisConsumption consumption, SupplySavings savings, boolean publishedData) {
        this.consumption = consumption;
        this.savings = savings;
        this.publishedData = publishedData;
    }

    /**
     * @param publishedData whether the supply has data in the bucket: its records carry published
     *                      self-consumption, as the series the bucket belongs to measures it
     */
    public static SupplyConsumptionBucket of(DatadisConsumption consumption, SupplySavings savings,
                                             boolean publishedData) {
        return new SupplyConsumptionBucket(Objects.requireNonNull(consumption),
                Objects.requireNonNull(savings), publishedData);
    }

    public DatadisConsumption getConsumption() {
        return consumption;
    }

    public SupplySavings getSavings() {
        return savings;
    }

    /**
     * Whether the supply has data in the bucket: its records carry published self-consumption. A
     * supply's self-consumption is published some days after the month ends; until then its records
     * carry consumption and surplus but no self-consumption, which is not a measured zero.
     */
    public boolean hasPublishedData() {
        return publishedData;
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
                ", publishedData=" + publishedData +
                '}';
    }
}
