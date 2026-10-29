package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One local calendar month of a membership's consumption series: the monthly buckets of every
 * supply of the membership that stored a record that month, folded together by
 * {@link MembershipConsumptionTotals}.
 */
public class MembershipMonthlyConsumptionBucket {

    private final YearMonth month;
    private final MembershipConsumptionTotals totals;

    private MembershipMonthlyConsumptionBucket(YearMonth month, MembershipConsumptionTotals totals) {
        this.month = month;
        this.totals = totals;
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
        return new MembershipMonthlyConsumptionBucket(month,
                MembershipConsumptionTotals.of(supplyCount, contributionsBySupply));
    }

    public YearMonth getMonth() {
        return month;
    }

    public MembershipConsumptionTotals getTotals() {
        return totals;
    }

    public BigDecimal getConsumptionKWh() {
        return totals.getConsumptionKWh();
    }

    public BigDecimal getSurplusEnergyKWh() {
        return totals.getSurplusEnergyKWh();
    }

    public BigDecimal getSelfConsumptionEnergyKWh() {
        return totals.getSelfConsumptionEnergyKWh();
    }

    /**
     * Self-consumed plus surplus energy over every supply of the month, derived from the unrounded
     * sums.
     */
    public double getAssignedProductionKWh() {
        return totals.getAssignedProductionKWh();
    }

    /**
     * The month's savings across every supply that stored a record, or empty when none did.
     */
    public Optional<SupplySavings> getSavings() {
        return totals.getSavings();
    }

    public int getSupplyCount() {
        return totals.getSupplyCount();
    }

    /**
     * The number of distinct supplies that stored a monthly record this month.
     */
    public int getSuppliesWithData() {
        return totals.getSuppliesWithData();
    }
}
