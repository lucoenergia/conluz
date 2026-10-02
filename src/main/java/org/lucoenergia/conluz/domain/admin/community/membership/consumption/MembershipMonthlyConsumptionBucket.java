package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisEnergyValues;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * One local calendar month of a membership's consumption series: the monthly buckets of every
 * supply of the membership that stored a record that month, folded together.
 *
 * <p>The energy figures are the sums of the supplies' own monthly figures, and the savings the
 * supplies' own monthly savings folded by {@link SupplySavings#total(List)}, so a month's figures are
 * exactly what the per-supply series reports for it, added up.
 *
 * <p>A month in which no supply stored a record has no savings at all rather than savings of zero:
 * a savings point at zero and a month with nothing stored are different statements. A supply that
 * did store a record with zero energy is a measured zero, so it counts as a supply with data and
 * its month has savings of zero.
 */
public class MembershipMonthlyConsumptionBucket {

    private final YearMonth month;
    private final BigDecimal consumptionKWh;
    private final BigDecimal surplusEnergyKWh;
    private final BigDecimal generationEnergyKWh;
    private final BigDecimal selfConsumptionEnergyKWh;
    private final SupplySavings savings;
    private final int supplyCount;
    private final int suppliesWithData;

    private MembershipMonthlyConsumptionBucket(YearMonth month, BigDecimal consumptionKWh,
                                               BigDecimal surplusEnergyKWh, BigDecimal generationEnergyKWh,
                                               BigDecimal selfConsumptionEnergyKWh, SupplySavings savings,
                                               int supplyCount, int suppliesWithData) {
        this.month = month;
        this.consumptionKWh = consumptionKWh;
        this.surplusEnergyKWh = surplusEnergyKWh;
        this.generationEnergyKWh = generationEnergyKWh;
        this.selfConsumptionEnergyKWh = selfConsumptionEnergyKWh;
        this.savings = savings;
        this.supplyCount = supplyCount;
        this.suppliesWithData = suppliesWithData;
    }

    /**
     * Folds the month's buckets of every supply that stored a record that month.
     *
     * @param supplyCount           the number of supplies the membership currently has in the community
     * @param contributionsBySupply the month's buckets, keyed by the supply that stored them; a
     *                              supply without a record that month is absent
     */
    public static MembershipMonthlyConsumptionBucket of(YearMonth month, int supplyCount,
                                                        Map<SupplyId, List<SupplyConsumptionBucket>> contributionsBySupply) {
        List<SupplyConsumptionBucket> contributions = contributionsBySupply.values().stream()
                .flatMap(List::stream)
                .toList();
        SupplySavings savings = contributions.isEmpty()
                ? null
                : SupplySavings.total(contributions.stream().map(SupplyConsumptionBucket::getSavings).toList());

        return new MembershipMonthlyConsumptionBucket(month,
                sum(contributions, DatadisConsumption::getConsumptionKWh),
                sum(contributions, DatadisConsumption::getSurplusEnergyKWh),
                sum(contributions, DatadisConsumption::getGenerationEnergyKWh),
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

    public YearMonth getMonth() {
        return month;
    }

    public BigDecimal getConsumptionKWh() {
        return consumptionKWh;
    }

    public BigDecimal getSurplusEnergyKWh() {
        return surplusEnergyKWh;
    }

    public BigDecimal getGenerationEnergyKWh() {
        return generationEnergyKWh;
    }

    public BigDecimal getSelfConsumptionEnergyKWh() {
        return selfConsumptionEnergyKWh;
    }

    /**
     * The month's savings across every supply that stored a record, or empty when none did.
     */
    public Optional<SupplySavings> getSavings() {
        return Optional.ofNullable(savings);
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    /**
     * The number of distinct supplies that stored a monthly record this month.
     */
    public int getSuppliesWithData() {
        return suppliesWithData;
    }
}
