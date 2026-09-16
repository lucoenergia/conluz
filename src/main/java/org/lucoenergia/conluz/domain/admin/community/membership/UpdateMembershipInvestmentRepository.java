package org.lucoenergia.conluz.domain.admin.community.membership;

import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateMembershipInvestmentRepository {

    /**
     * Writes {@code investmentEur} onto the membership, or clears it when the amount is null.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user has no membership in the community
     */
    void updateInvestment(UUID communityId, UUID userId, BigDecimal investmentEur);
}
