package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile;

import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsCoverage;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The average day of a membership over its latest published month: for each hour of the local
 * day, the average consumption and the average assigned production of every record of every supply
 * of the membership, as {@link HourlyProfileAccumulator} computes them, together with how much of
 * the month the records cover.
 */
public class MembershipHourlyProfile {

    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;
    private final MembershipEnergyMetricsCoverage coverage;
    private final List<HourlyProfileBucket> buckets;

    private MembershipHourlyProfile(OffsetDateTime startDate, OffsetDateTime endDate,
                                    MembershipEnergyMetricsCoverage coverage, List<HourlyProfileBucket> buckets) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.coverage = coverage;
        this.buckets = List.copyOf(buckets);
    }

    public static MembershipHourlyProfile of(EnergyMetricsPeriod period, MembershipEnergyMetricsCoverage coverage,
                                             List<HourlyProfileBucket> buckets) {
        return new MembershipHourlyProfile(period.getStartDate(), period.getEndDate(), coverage, buckets);
    }

    /**
     * The profile of a membership for which no month could be resolved: null bounds, no coverage
     * and 24 buckets without any sample.
     */
    public static MembershipHourlyProfile withoutPeriod(int supplyCount) {
        return new MembershipHourlyProfile(null, null, MembershipEnergyMetricsCoverage.none(supplyCount),
                IntStream.range(0, HourlyProfileAccumulator.HOURS_OF_DAY)
                        .mapToObj(HourlyProfileBucket::empty)
                        .toList());
    }

    /**
     * The first instant of the month, inclusive, or null when no month could be resolved.
     */
    public OffsetDateTime getStartDate() {
        return startDate;
    }

    /**
     * The last instant of the month, inclusive, or null when no month could be resolved.
     */
    public OffsetDateTime getEndDate() {
        return endDate;
    }

    public MembershipEnergyMetricsCoverage getCoverage() {
        return coverage;
    }

    /**
     * The 24 buckets, ordered from hour 0 to hour 23.
     */
    public List<HourlyProfileBucket> getBuckets() {
        return buckets;
    }
}
