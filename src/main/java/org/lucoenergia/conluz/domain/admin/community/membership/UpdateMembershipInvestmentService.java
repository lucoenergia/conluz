package org.lucoenergia.conluz.domain.admin.community.membership;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Records or removes the initial contribution a member made to a community.
 *
 * <p>Only the current value is kept: setting an investment overwrites whatever was there, and no
 * previous value is retained anywhere. A caller that needs the old amount has to have read it
 * first.
 */
public interface UpdateMembershipInvestmentService {

    /**
     * Records {@code investmentEur} as the membership's investment, replacing any existing value.
     *
     * @param investmentEur the amount in euros; must be greater than zero with at most two
     *                      decimals, which the request body validates before this is reached
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user has no membership in the community
     */
    void setInvestment(UUID communityId, UUID userId, BigDecimal investmentEur);

    /**
     * Removes the membership's investment, returning it to "none recorded".
     *
     * <p>Idempotent on a membership that has no investment: the end state is the one the caller
     * asked for, so there is nothing to report.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user has no membership in the community
     */
    void clearInvestment(UUID communityId, UUID userId);
}
