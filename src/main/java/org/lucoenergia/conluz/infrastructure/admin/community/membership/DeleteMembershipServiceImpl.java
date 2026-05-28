package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DeleteMembershipServiceImpl implements DeleteMembershipService {

    private final CommunityMembershipJpaRepository membershipJpaRepository;

    public DeleteMembershipServiceImpl(CommunityMembershipJpaRepository membershipJpaRepository) {
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
