package org.lucoenergia.conluz.infrastructure.admin.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommunityMembershipJpaRepository extends JpaRepository<CommunityMembershipEntity, UUID> {

    List<CommunityMembershipEntity> findByUserId(UUID userId);

    List<CommunityMembershipEntity> findByCommunityId(UUID communityId);
}
