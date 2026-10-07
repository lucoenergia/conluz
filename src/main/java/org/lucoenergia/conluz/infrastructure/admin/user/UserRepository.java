package org.lucoenergia.conluz.infrastructure.admin.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByPersonalId(String personalId);

    /**
     * Locks the user row until the end of the transaction. {@code NO KEY UPDATE} conflicts with itself, so
     * concurrent callers for the same user run one after another, but not with the {@code KEY SHARE} lock that
     * foreign key checks from other tables take. The same lock the one-time token store takes before it writes a
     * user's tokens.
     *
     * @return the user's id, or empty if the user does not exist
     */
    @Query(value = "SELECT id FROM users WHERE id = :userId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockById(@Param("userId") UUID userId);

    Optional<UserEntity> findFirstByNumber(int number);

    boolean existsByPersonalId(String personalId);

    boolean existsByPersonalIdAndIdNot(String personalId, UUID id);

    long countByIsPlatformAdminTrueAndEnabledTrue();

    @Query("SELECT u FROM users u WHERE EXISTS(SELECT s FROM supplies s WHERE s.user = u)")
    List<UserEntity> findAllUsersWithAtLeastOneSupply();

    /**
     * The given user plus every user with an enabled membership in any of the given communities, in
     * one statement however many communities there are. A filter rather than a join, so each user
     * appears once and the given user sorts among the others. {@code communityIds} must not be empty:
     * {@code IN ()} is not valid SQL.
     */
    @Query("SELECT u FROM users u WHERE u.id = :selfId OR EXISTS (SELECT 1 FROM community_memberships m "
            + "WHERE m.user = u AND m.enabled = true AND m.community.id IN :communityIds)")
    Page<UserEntity> findAllVisible(@Param("selfId") UUID selfId,
                                    @Param("communityIds") Collection<UUID> communityIds,
                                    Pageable pageable);

    Page<UserEntity> findByIdIn(Collection<UUID> ids, Pageable pageable);
}
