package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class DeleteMembershipRepositoryDatabase implements DeleteMembershipRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;

    public DeleteMembershipRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
    }

    /**
     * Deleting a membership that is not there is reported rather than passing silently. It used to
     * answer success after deleting nothing, which tells a caller who removed the wrong user, or
     * named the wrong community, that they succeeded.
     *
     * <p>Not made idempotent on purpose, unlike clearing an investment: that clears a field on a
     * membership the caller has already been told exists, whereas this addresses the membership
     * itself, and "it is gone" and "it was never there" are different answers to a request that
     * named it.
     */
    @Override
    public void delete(UUID communityId, UUID userId) {
        CommunityMembershipEntity membership = membershipJpaRepository
                .findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId));

        membershipJpaRepository.delete(membership);
    }
}
