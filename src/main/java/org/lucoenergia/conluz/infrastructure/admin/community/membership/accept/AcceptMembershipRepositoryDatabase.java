package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.AcceptMembershipRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Transactional
@Repository
public class AcceptMembershipRepositoryDatabase implements AcceptMembershipRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;

    public AcceptMembershipRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
    }

    @Override
    public void accept(UserId userId, UUID communityId, Instant acceptedAt) {
        CommunityMembershipEntity entity = membershipJpaRepository
                .findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));

        entity.setAcceptedAt(acceptedAt);
        membershipJpaRepository.save(entity);
    }
}
