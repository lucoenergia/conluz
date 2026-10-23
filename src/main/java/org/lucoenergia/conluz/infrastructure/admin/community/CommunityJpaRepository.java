package org.lucoenergia.conluz.infrastructure.admin.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityJpaRepository extends JpaRepository<CommunityEntity, UUID> {

    Optional<CommunityEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByLegalId(String legalId);

    /**
     * Whether a community <em>other than</em> {@code id} already uses this code. The exclusion is
     * what makes the check usable on update: without it, a community keeping its own code would
     * collide with itself.
     */
    boolean existsByCodeAndIdNot(String code, UUID id);

    /**
     * Whether a community <em>other than</em> {@code id} already uses this legal id. Excludes the
     * row being updated for the same reason as {@link #existsByCodeAndIdNot(String, UUID)}.
     */
    boolean existsByLegalIdAndIdNot(String legalId, UUID id);

    @Query("SELECT c.id FROM communities c")
    List<UUID> findAllIds();

    /**
     * Locks the community row until the end of the caller's transaction. {@code FOR NO KEY UPDATE} rather than
     * {@code FOR UPDATE}, so it does not conflict with the {@code KEY SHARE} lock that foreign key checks from other
     * tables take. Lock it before the user and membership rows.
     *
     * @return the community's id, or empty if there is no such community
     */
    @Query(value = "SELECT id FROM communities WHERE id = :communityId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockById(@Param("communityId") UUID communityId);
}
