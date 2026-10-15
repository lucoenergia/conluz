package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.SetMembershipEnabledRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class SetMembershipEnabledRepositoryDatabase implements SetMembershipEnabledRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;

    public SetMembershipEnabledRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
    }

    @Override
    public boolean setEnabled(UUID communityId, UserId userId, CommunityRole role, boolean enabled) {
        // Written through the entity rather than an update query: the membership's state is only ever filtered with
        // the shared predicates, and a role check belongs with the row it reads
        Optional<CommunityMembershipEntity> membership = membershipJpaRepository
                .findByUserIdAndCommunityId(userId.getId(), communityId)
                .filter(entity -> entity.getRole() == role);
        membership.ifPresent(entity -> {
            entity.setEnabled(enabled);
            membershipJpaRepository.saveAndFlush(entity);
        });
        return membership.isPresent();
    }
}
