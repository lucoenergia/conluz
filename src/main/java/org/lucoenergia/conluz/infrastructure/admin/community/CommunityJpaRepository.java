package org.lucoenergia.conluz.infrastructure.admin.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
