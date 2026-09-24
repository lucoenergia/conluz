package org.lucoenergia.conluz.domain.admin.community.membership.payback;

import java.util.UUID;

public interface GetMembershipPaybackService {

    /**
     * How much of this member's investment their share of the community's energy has recovered.
     *
     * <p>Nothing is persisted: the savings figure is priced on every call from the consumption
     * already stored, so it reflects both new consumption and any correction to the tariff or the
     * coefficients since the last read.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user has no membership in the community
     */
    MembershipPayback getPayback(UUID communityId, UUID userId);
}
