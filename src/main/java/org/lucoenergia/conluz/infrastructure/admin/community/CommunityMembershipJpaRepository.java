package org.lucoenergia.conluz.infrastructure.admin.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CommunityMembershipJpaRepository extends JpaRepository<CommunityMembershipEntity, UUID> {

    List<CommunityMembershipEntity> findByUserId(UUID userId);

    List<CommunityMembershipEntity> findByCommunityId(UUID communityId);

    @Query("SELECT m.community.id, COUNT(m) FROM community_memberships m WHERE m.community.id IN :ids GROUP BY m.community.id")
    List<Object[]> countMembersByCommunityIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT m.community.id, u.fullName FROM community_memberships m JOIN m.user u WHERE m.community.id IN :ids AND m.role = 'COMMUNITY_ADMIN'")
    List<Object[]> findAdminNamesByCommunityIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT m FROM community_memberships m LEFT JOIN FETCH m.user WHERE m.community.id = :communityId")
    List<CommunityMembershipEntity> findByCommunityIdWithUser(@Param("communityId") UUID communityId);
}
