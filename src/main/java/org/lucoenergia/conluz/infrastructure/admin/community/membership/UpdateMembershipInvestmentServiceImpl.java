package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipInvestmentRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipInvestmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
public class UpdateMembershipInvestmentServiceImpl implements UpdateMembershipInvestmentService {

    private final UpdateMembershipInvestmentRepository updateMembershipInvestmentRepository;

    public UpdateMembershipInvestmentServiceImpl(
            UpdateMembershipInvestmentRepository updateMembershipInvestmentRepository) {
        this.updateMembershipInvestmentRepository = updateMembershipInvestmentRepository;
    }

    @Override
    public void setInvestment(UUID communityId, UUID userId, BigDecimal investmentEur) {
        updateMembershipInvestmentRepository.updateInvestment(communityId, userId, investmentEur);
    }

    /**
     * Clearing is the same write with no amount, so both operations share one repository method
     * and therefore one existence check: a DELETE against a membership that is not there answers
     * 404 rather than succeeding silently.
     */
    @Override
    public void clearInvestment(UUID communityId, UUID userId) {
        updateMembershipInvestmentRepository.updateInvestment(communityId, userId, null);
    }
}
