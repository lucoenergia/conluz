package org.lucoenergia.conluz.infrastructure.admin.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CommunityMembershipJpaRepository extends JpaRepository<CommunityMembershipEntity, UUID> {

    List<CommunityMembershipEntity> findByUserId(UUID userId);

    List<CommunityMembershipEntity> findByCommunityId(UUID communityId);

    /**
     * The one membership of a user in a community, if it exists. At most one row can match: the
     * {@code community_memberships_user_community_uq} constraint makes the pair unique. Prefer
     * this over filtering {@link #findByUserId(UUID)} in memory, which loads every community the
     * user belongs to in order to keep one row.
     */
    Optional<CommunityMembershipEntity> findByUserIdAndCommunityId(UUID userId, UUID communityId);

    /**
     * Whether the user already belongs to the community. Cheaper than
     * {@link #findByUserIdAndCommunityId(UUID, UUID)} when the row itself is not needed, which is
     * the case for the duplicate precondition on create.
     */
    boolean existsByUserIdAndCommunityId(UUID userId, UUID communityId);

    @Query("SELECT m.community.id, COUNT(m) FROM community_memberships m WHERE m.community.id IN :ids GROUP BY m.community.id")
    List<Object[]> countMembersByCommunityIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT m.community.id, u.fullName FROM community_memberships m JOIN m.user u WHERE m.community.id IN :ids AND m.role = 'COMMUNITY_ADMIN'")
    List<Object[]> findAdminNamesByCommunityIds(@Param("ids") Set<UUID> ids);

    /**
     * Finds all memberships for a given community, eagerly fetching the associated user.
     * <p>
     * The {@code WithUser} suffix indicates this method uses {@code LEFT JOIN FETCH m.user}
     * to avoid N+1 lazy-load queries when traversing the user relation. Prefer this over
     * {@link #findByCommunityId(UUID)} when the caller needs user data (e.g. for building
     * response DTOs).
     *
     * @param communityId the community ID
     * @return list of memberships with their users loaded
     */
    @Query("SELECT m FROM community_memberships m LEFT JOIN FETCH m.user WHERE m.community.id = :communityId")
    List<CommunityMembershipEntity> findByCommunityIdWithUser(@Param("communityId") UUID communityId);

    /**
     * Finds all memberships for the given set of users, eagerly fetching both the associated
     * community and user.
     * <p>
     * The {@code WithCommunityAndUser} suffix indicates this method uses
     * {@code LEFT JOIN FETCH m.community LEFT JOIN FETCH m.user} to avoid N+1 lazy-load queries
     * when building response DTOs for a page of users. Prefer this over per-user calls to
     * {@link #findByUserId(UUID)} when enriching multiple users at once.
     *
     * @param userIds the user IDs
     * @return list of memberships with their community and user loaded
     */
    @Query("SELECT m FROM community_memberships m " +
            "LEFT JOIN FETCH m.community LEFT JOIN FETCH m.user " +
            "WHERE m.user.id IN :userIds")
    List<CommunityMembershipEntity> findByUserIdInWithCommunityAndUser(@Param("userIds") Collection<UUID> userIds);
}
