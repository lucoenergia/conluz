package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One local calendar day of a membership's consumption series: the daily buckets of every supply of
 * the membership that stored at least one record that day, folded together by
 * {@link MembershipConsumptionTotals}.
 */
public class MembershipDailyConsumptionBucket {

    private final LocalDate day;
    private final MembershipConsumptionTotals totals;

    private MembershipDailyConsumptionBucket(LocalDate day, MembershipConsumptionTotals totals) {
        this.day = day;
        this.totals = totals;
    }

    /**
     * Folds the day's buckets of every supply that stored at least one record that day.
     *
     * @param supplyCount           the number of supplies the membership currently has in the community
     * @param contributionsBySupply the day's buckets, keyed by the supply that stored them; a supply
     *                              without a record that day is absent
     */
    public static MembershipDailyConsumptionBucket of(LocalDate day, int supplyCount,
                                                      Map<SupplyId, List<SupplyConsumptionBucket>> contributionsBySupply) {
        return new MembershipDailyConsumptionBucket(day,
                MembershipConsumptionTotals.of(supplyCount, contributionsBySupply));
    }

    public LocalDate getDay() {
        return day;
    }

    public MembershipConsumptionTotals getTotals() {
        return totals;
    }
}
