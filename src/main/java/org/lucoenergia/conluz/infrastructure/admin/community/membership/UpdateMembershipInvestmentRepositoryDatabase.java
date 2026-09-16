package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipInvestmentRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Transactional
@Repository
public class UpdateMembershipInvestmentRepositoryDatabase implements UpdateMembershipInvestmentRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;

    public UpdateMembershipInvestmentRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
    }

    @Override
    public void updateInvestment(UUID communityId, UUID userId, BigDecimal investmentEur) {
        CommunityMembershipEntity entity = membershipJpaRepository
                .findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId));

        entity.setInvestmentEur(investmentEur);
        membershipJpaRepository.save(entity);
    }
}
