package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.hourlyprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsCoverage;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.MembershipHourlyProfile;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.MembershipEnergyMetricsCoverageResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.SupplyEnergyMetricsPeriodResponse;

import java.util.List;

@Schema(requiredProperties = {"period", "coverage", "buckets"})
public class MembershipHourlyProfileResponse {

    @Schema(description = "The latest published month the profile was computed over. Both bounds are " +
            "null when no month could be resolved.")
    private final SupplyEnergyMetricsPeriodResponse period;
    @Schema(description = "How much of the month is backed by stored records, across every supply of " +
            "the membership, counted exactly as the aggregated energy metrics count it.")
    private final MembershipEnergyMetricsCoverageResponse coverage;
    @Schema(description = "Exactly 24 buckets, one per hour of the local day, ordered from hour 0 to " +
            "hour 23, including the hours without any sample.")
    private final List<MembershipHourlyProfileBucketResponse> buckets;

    public MembershipHourlyProfileResponse(MembershipHourlyProfile profile) {
        MembershipEnergyMetricsCoverage coverage = profile.getCoverage();
        this.period = new SupplyEnergyMetricsPeriodResponse(profile.getStartDate(), profile.getEndDate());
        this.coverage = new MembershipEnergyMetricsCoverageResponse(
                coverage.getHoursWithData(),
                coverage.getExpectedHours(),
                coverage.getSupplyCount(),
                coverage.getSuppliesWithData());
        this.buckets = profile.getBuckets().stream().map(MembershipHourlyProfileBucketResponse::new).toList();
    }

    public SupplyEnergyMetricsPeriodResponse getPeriod() {
        return period;
    }

    public MembershipEnergyMetricsCoverageResponse getCoverage() {
        return coverage;
    }

    public List<MembershipHourlyProfileBucketResponse> getBuckets() {
        return buckets;
    }
}
