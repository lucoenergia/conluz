package org.lucoenergia.conluz.infrastructure.admin.community;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CommunityMembershipJpaRepository extends JpaRepository<CommunityMembershipEntity, UUID> {

    /**
     * Finds all memberships of one user, eagerly fetching both the community and the user.
     *
     * <p>The single-user counterpart of {@link #findByUserIdInWithCommunityAndUser(Collection)}, and
     * the one every authenticated request runs: the security principal is loaded this way, and
     * {@code CallerMemberships} then reads the community id of each membership to answer every
     * access rule. Without the fetch those reads are a lazy select per membership, on every request
     * of every session.</p>
     *
     * @param userId the user ID
     * @return list of memberships with their community and user loaded
     */
    @Query("SELECT m FROM community_memberships m " +
            "LEFT JOIN FETCH m.community LEFT JOIN FETCH m.user " +
            "WHERE m.user.id = :userId")
    List<CommunityMembershipEntity> findByUserIdWithCommunityAndUser(@Param("userId") UUID userId);

    List<CommunityMembershipEntity> findByCommunityId(UUID communityId);

    /**
     * The one membership of a user in a community, if it exists. At most one row can match: the
     * {@code community_memberships_user_community_uq} constraint makes the pair unique. Prefer
     * this over filtering {@link #findByUserIdWithCommunityAndUser(UUID)} in memory, which loads every community the
     * user belongs to in order to keep one row.
     */
    Optional<CommunityMembershipEntity> findByUserIdAndCommunityId(UUID userId, UUID communityId);

    /**
     * Whether the user already belongs to the community. Cheaper than
     * {@link #findByUserIdAndCommunityId(UUID, UUID)} when the row itself is not needed, which is
     * the case for the duplicate precondition on create.
     */
    boolean existsByUserIdAndCommunityId(UUID userId, UUID communityId);

    /**
     * Locks the user's membership in the community until the end of the transaction. {@code NO KEY UPDATE}
     * conflicts with itself, so concurrent callers for the same membership run one after another, but not with the
     * {@code KEY SHARE} lock that foreign key checks from other tables take. The same lock the one-time token store
     * takes before it writes a membership's tokens. Lock the membership's user row first.
     *
     * @return the membership's id, or empty if the user has no membership in the community
     */
    @Query(value = "SELECT id FROM community_memberships WHERE user_id = :userId AND community_id = :communityId "
            + "FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockByUserIdAndCommunityId(@Param("userId") UUID userId, @Param("communityId") UUID communityId);

    /**
     * The number of active members of each given community; a pending or disabled membership is not
     * counted.
     */
    @Query("SELECT m.community.id, COUNT(m) FROM community_memberships m WHERE m.community.id IN :ids AND "
            + ActiveMembershipJpql.ACTIVE_MEMBERSHIP + " GROUP BY m.community.id")
    List<Object[]> countMembersByCommunityIds(@Param("ids") Set<UUID> ids);

    /**
     * The names of the active admins of each given community; a pending or disabled admin membership
     * is not named.
     */
    @Query("SELECT m.community.id, u.fullName FROM community_memberships m JOIN m.user u WHERE m.community.id IN :ids "
            + "AND m.role = 'COMMUNITY_ADMIN' AND " + ActiveMembershipJpql.ACTIVE_MEMBERSHIP)
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
     * {@link #findByUserIdWithCommunityAndUser(UUID)} when enriching multiple users at once.
     *
     * @param userIds the user IDs
     * @return list of memberships with their community and user loaded
     */
    @Query("SELECT m FROM community_memberships m " +
            "LEFT JOIN FETCH m.community LEFT JOIN FETCH m.user " +
            "WHERE m.user.id IN :userIds")
    List<CommunityMembershipEntity> findByUserIdInWithCommunityAndUser(@Param("userIds") Collection<UUID> userIds);

    /**
     * The users of the community's memberships eligible for a first invitation (MEM-012), in ascending user id.
     */
    @Query("SELECT m.user.id FROM community_memberships m WHERE m.community.id = :communityId "
            + "AND m.contactEmail IS NOT NULL AND m.invitedAt IS NULL AND "
            + ActiveMembershipJpql.AWAITING_ACCEPTANCE + " ORDER BY m.user.id")
    List<UUID> findFirstInvitationCandidateUserIds(@Param("communityId") UUID communityId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM community_memberships m WHERE m.community.id = :communityId "
            + "AND m.contactEmail IS NOT NULL AND m.invitedAt IS NULL AND "
            + ActiveMembershipJpql.AWAITING_ACCEPTANCE)
    long countFirstInvitationCandidates(@Param("communityId") UUID communityId);
}
