package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.consumption.EnergyBalance;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisEnergyValues;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The figures of one bucket of a membership's consumption series, whatever its length: the buckets
 * of every supply of the membership that stored data in it, folded together.
 *
 * <p>The energy figures are the sums of the supplies' own figures, and the savings the supplies' own
 * savings folded by {@link SupplySavings#total(List)}, so a bucket's figures are exactly what the
 * per-supply series reports for it, added up. The assigned production is derived from the unrounded
 * sums of self-consumed and surplus energy, by
 * {@link EnergyBalance#assignedProductionKWh(double, double)}, never from the distributor's
 * generation figure (#460).
 *
 * <p>A bucket to which no supply contributed has no savings at all rather than savings of zero: a
 * savings point at zero and a bucket with nothing stored are different statements. A supply that
 * did store a record with zero energy is a measured zero, so it counts as a supply with data and
 * the bucket has savings of zero.
 */
public class MembershipConsumptionTotals {

    private final BigDecimal consumptionKWh;
    private final BigDecimal surplusEnergyKWh;
    private final BigDecimal selfConsumptionEnergyKWh;
    private final double assignedProductionKWh;
    private final SupplySavings savings;
    private final int supplyCount;
    private final int suppliesWithData;

    private MembershipConsumptionTotals(BigDecimal consumptionKWh, BigDecimal surplusEnergyKWh,
                                        BigDecimal selfConsumptionEnergyKWh, SupplySavings savings,
                                        int supplyCount, int suppliesWithData) {
        this.consumptionKWh = consumptionKWh;
        this.surplusEnergyKWh = surplusEnergyKWh;
        this.selfConsumptionEnergyKWh = selfConsumptionEnergyKWh;
        this.assignedProductionKWh = EnergyBalance.assignedProductionKWh(selfConsumptionEnergyKWh.doubleValue(),
                surplusEnergyKWh.doubleValue());
        this.savings = savings;
        this.supplyCount = supplyCount;
        this.suppliesWithData = suppliesWithData;
    }

    /**
     * Folds the buckets of every supply that stored data in this bucket.
     *
     * @param supplyCount           the number of supplies the membership currently has in the community
     * @param contributionsBySupply the buckets, keyed by the supply that stored them; a supply that
     *                              stored nothing in this bucket is absent
     */
    public static MembershipConsumptionTotals of(int supplyCount,
                                                 Map<SupplyId, List<SupplyConsumptionBucket>> contributionsBySupply) {
        List<SupplyConsumptionBucket> contributions = contributionsBySupply.values().stream()
                .flatMap(List::stream)
                .toList();
        SupplySavings savings = contributions.isEmpty()
                ? null
                : SupplySavings.total(contributions.stream().map(SupplyConsumptionBucket::getSavings).toList());

        return new MembershipConsumptionTotals(
                sum(contributions, DatadisConsumption::getConsumptionKWh),
                sum(contributions, DatadisConsumption::getSurplusEnergyKWh),
                sum(contributions, DatadisConsumption::getSelfConsumptionEnergyKWh),
                savings,
                supplyCount,
                contributionsBySupply.size());
    }

    private static BigDecimal sum(List<SupplyConsumptionBucket> contributions,
                                  Function<DatadisConsumption, Float> field) {
        return contributions.stream()
                .map(contribution -> DatadisEnergyValues.exactlyAsReported(field.apply(contribution.getConsumption())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getConsumptionKWh() {
        return consumptionKWh;
    }

    public BigDecimal getSurplusEnergyKWh() {
        return surplusEnergyKWh;
    }

    public BigDecimal getSelfConsumptionEnergyKWh() {
        return selfConsumptionEnergyKWh;
    }

    /**
     * Self-consumed plus surplus energy over every supply, derived from the unrounded sums.
     */
    public double getAssignedProductionKWh() {
        return assignedProductionKWh;
    }

    /**
     * The savings across every supply that stored data, or empty when none did.
     */
    public Optional<SupplySavings> getSavings() {
        return Optional.ofNullable(savings);
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    /**
     * The number of distinct supplies that stored data in this bucket.
     */
    public int getSuppliesWithData() {
        return suppliesWithData;
    }
}
