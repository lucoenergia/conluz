package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile;

import java.util.UUID;

public interface GetMembershipHourlyProfileService {

    /**
     * The average day of every supply the member owns in the community, over the latest published
     * month resolved for them exactly as the aggregated energy metrics resolve it. When no month
     * can be resolved, the profile has no period and 24 buckets without any sample.
     *
     * <p>The period cannot be chosen: only inside a month with published assigned production is a
     * stored zero a measured zero rather than a value not published yet.</p>
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user holds no membership in the community
     */
    MembershipHourlyProfile getHourlyProfile(UUID communityId, UUID userId);
}
