package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional
@Repository
public class DeleteMembershipRepositoryDatabase implements DeleteMembershipRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;

    public DeleteMembershipRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
    }

    @Override
    public void delete(UUID communityId, UUID userId) {
        List<CommunityMembershipEntity> memberships = membershipJpaRepository.findByUserId(userId).stream()
                .filter(m -> m.getCommunity() != null && communityId.equals(m.getCommunity().getId()))
                .toList();
        membershipJpaRepository.deleteAll(memberships);
    }
}
