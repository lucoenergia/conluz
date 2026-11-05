package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One hour of a membership's consumption series: the hourly buckets of every supply of the
 * membership that stored a record that hour, folded together by {@link MembershipConsumptionTotals}.
 *
 * <p>The start is what identifies the hour. Its local date and time labels do not on the day the
 * clocks go back, when two consecutive hours carry the same ones.
 */
public class MembershipHourlyConsumptionBucket {

    private final OffsetDateTime startsAt;
    private final MembershipConsumptionTotals totals;

    private MembershipHourlyConsumptionBucket(OffsetDateTime startsAt, MembershipConsumptionTotals totals) {
        this.startsAt = startsAt;
        this.totals = totals;
    }

    /**
     * Folds the hour's buckets of every supply that stored a record that hour.
     *
     * @param startsAt              the instant the hour starts at, with the offset in force in the
     *                              community's time zone at that instant
     * @param supplyCount           the number of supplies the membership currently has in the community
     * @param contributionsBySupply the hour's buckets, keyed by the supply that stored them; a supply
     *                              without a record that hour is absent
     */
    public static MembershipHourlyConsumptionBucket of(OffsetDateTime startsAt, int supplyCount,
                                                       Map<SupplyId, List<SupplyConsumptionBucket>> contributionsBySupply) {
        return new MembershipHourlyConsumptionBucket(Objects.requireNonNull(startsAt),
                MembershipConsumptionTotals.of(supplyCount, contributionsBySupply));
    }

    /**
     * The instant the hour starts at, with the offset in force in the community's time zone at that
     * instant.
     */
    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public MembershipConsumptionTotals getTotals() {
        return totals;
    }
}
